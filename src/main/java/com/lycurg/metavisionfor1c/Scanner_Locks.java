package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class Scanner_Locks {

    public static class LockIssue {
        public String type;
        public String severity;
        public String description;
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public String filePath;
        public int lineNumber;
        public String snippet;
        public String recommendation;

        public LockIssue(String type, String severity, String description,
                         String functionName, String objectFullName, String moduleType,
                         String filePath, int lineNumber, String snippet,
                         String recommendation) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.functionName = functionName;
            this.objectFullName = objectFullName;
            this.moduleType = moduleType;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.snippet = snippet;
            this.recommendation = recommendation;
        }
    }

    // ── Паттерны ────────────────────────────────────────────────────────────────
    private static final Pattern MANAGED_LOCK_ADD = Pattern.compile(
            "БлокировкаДанных\\.Добавить\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern MANAGED_LOCK_LOCK = Pattern.compile(
            "\\.Заблокировать\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern SET_VALUE = Pattern.compile(
            "\\.УстановитьЗначение\\s*\\(", Pattern.CASE_INSENSITIVE);

    // Начало цикла — строго по ключевым словам 1С
    private static final Pattern LOOP_START = Pattern.compile(
            "^\\s*(Для\\s+Каждого\\b|Для\\s+\\w+\\s*=|Пока\\s+)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern LOOP_END = Pattern.compile(
            "^\\s*КонецЦикла\\s*;",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // ════════════════════════════════════════════════════════════════════════════
    //  ТОЧКА ВХОДА
    // ════════════════════════════════════════════════════════════════════════════
    public static List<LockIssue> scanForLockIssues() {
        List<LockIssue> issues = new CopyOnWriteArrayList<>();

        // Загружаем рекурсивные функции из БД
        Set<String> recursiveFunctionKeys = loadRecursiveFunctions();

        String sql = """
            SELECT
                mf.function_name,
                mf.function_text,
                mf.start_line,
                mm.object_full_name,
                mm.module_type,
                mm.file_path
            FROM metadata_functions mf
            JOIN metadata_modules mm ON mf.module_id = mm.id
            WHERE mf.function_text IS NOT NULL
              AND (
                  mf.function_text LIKE '%БлокировкаДанных%'
                  OR mf.function_text LIKE '%Заблокировать%'
              )
            ORDER BY mm.object_full_name, mf.function_name
            """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String functionName   = rs.getString("function_name");
                String functionText   = rs.getString("function_text");
                String objectFullName = rs.getString("object_full_name");
                String moduleType     = rs.getString("module_type");
                String filePath       = rs.getString("file_path");
                int    startLine      = rs.getInt("start_line");

                analyzeFunctionForLockIssues(
                        functionName, functionText, objectFullName,
                        moduleType, filePath, startLine,
                        recursiveFunctionKeys, issues);
            }

            System.out.println("Найдено проблем с блокировками: " + issues.size());

        } catch (SQLException e) {
            System.err.println("Ошибка анализа блокировок: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Загрузка рекурсивных функций из таблицы recursive_functions
    //  Ключ: "objectFullName::functionName"
    // ════════════════════════════════════════════════════════════════════════════
    private static Set<String> loadRecursiveFunctions() {
        Set<String> keys = new HashSet<>();
        String sql = "SELECT function_name, object_full_name FROM recursive_functions";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String fn  = rs.getString("function_name");
                String obj = rs.getString("object_full_name");
                if (fn != null && obj != null) {
                    keys.add(obj + "::" + fn);
                }
            }
        } catch (SQLException e) {
            // Таблица может отсутствовать в старых БД — не критично
            System.err.println("Предупреждение: не удалось загрузить recursive_functions: " + e.getMessage());
        }
        return keys;
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  ДИСПЕТЧЕР ПРОВЕРОК
    // ════════════════════════════════════════════════════════════════════════════
    private static void analyzeFunctionForLockIssues(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            Set<String> recursiveFunctionKeys,
            List<LockIssue> issues) {

        String[] lines = functionText.split("\n");

        // 1. Добавили элементы, но не вызвали Заблокировать()
        checkAddWithoutLock(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines, issues);

        // 2. Заблокировать() внутри цикла
        checkLockInsideLoop(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines, issues);

        // 3. Рекурсивная функция с блокировками
        checkRecursiveFunctionWithLock(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines,
                recursiveFunctionKeys, issues);

    }

    // ════════════════════════════════════════════════════════════════════════════
    //  1. Добавили элементы, но не вызвали Заблокировать()
    // ════════════════════════════════════════════════════════════════════════════
    private static void checkAddWithoutLock(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, List<LockIssue> issues) {

        Matcher addMatcher = MANAGED_LOCK_ADD.matcher(functionText);
        if (!addMatcher.find()) return;

        boolean hasLock = MANAGED_LOCK_LOCK.matcher(functionText).find();
        if (!hasLock) {
            int lineNum = countNewlines(functionText.substring(0, addMatcher.start())) + 1;
            issues.add(new LockIssue(
                    "Добавлены элементы без Заблокировать()",
                    "HIGH",
                    "Вызов БлокировкаДанных.Добавить() есть, но .Заблокировать() нигде в функции не вызывается. " +
                            "Блокировка фактически не установлена — объект БлокировкаДанных создан и заполнен, " +
                            "но на сервер команда блокировки не отправлена. Код работает без ошибок, " +
                            "но при параллельной работе нескольких пользователей данные не защищены.",
                    functionName, objectFullName, moduleType, filePath, lineNum,
                    getLine(lines, lineNum - startLine - 1),
                    "После добавления всех элементов через Добавить() обязательно вызовите .Заблокировать(). " +
                            "Пример: Блокировка = Новый БлокировкаДанных; " +
                            "ЭлементБлокировки = Блокировка.Добавить(\"РегистрНакопления.ОстаткиТоваров\"); " +
                            "ЭлементБлокировки.УстановитьЗначение(\"Номенклатура\", Номенклатура); " +
                            "Блокировка.Заблокировать();"
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  2. Заблокировать() внутри цикла
    //
    //  Алгоритм: построчно отслеживаем вложенность циклов.
    //  Это исключает ложные срабатывания от regex-матчинга по всему тексту.
    // ════════════════════════════════════════════════════════════════════════════
    private static void checkLockInsideLoop(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, List<LockIssue> issues) {

        int loopDepth = 0;
        boolean reported = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Увеличиваем глубину при входе в цикл
            if (LOOP_START.matcher(line).find()) {
                loopDepth++;
            }

            // Проверяем Заблокировать() внутри цикла
            if (!reported && loopDepth > 0 && MANAGED_LOCK_LOCK.matcher(line).find()) {
               // int lineNum = startLine + i;
                int lineNum = i + 1;
                issues.add(new LockIssue(
                        "Заблокировать() внутри цикла",
                        "MEDIUM",
                        "Вызов .Заблокировать() обнаружен внутри цикла. " +
                                "Каждый вызов Заблокировать() — это обращение к серверу блокировок. " +
                                "В цикле это N round-trip'ов к серверу вместо одного, плюс каждая итерация " +
                                "удерживает блокировку до конца транзакции. При длинном цикле другие " +
                                "пользователи будут ждать. Также возможна взаимоблокировка: поток A " +
                                "заблокировал запись 1 и ждёт запись 2, поток B заблокировал запись 2 " +
                                "и ждёт запись 1.",
                        functionName, objectFullName, moduleType, filePath, lineNum,
                        getLine(lines, i),
                        "Соберите все нужные значения до начала цикла, добавьте все элементы " +
                                "в БлокировкаДанных через Добавить() + УстановитьЗначение(), " +
                                "затем вызовите Заблокировать() один раз перед циклом. " +
                                "Один вызов Заблокировать() блокирует все добавленные элементы атомарно."
                ));
                reported = true; // одно сообщение на функцию, не засоряем
            }

            // Уменьшаем глубину при выходе из цикла
            if (LOOP_END.matcher(line).find() && loopDepth > 0) {
                loopDepth--;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  3. Рекурсивная функция с блокировками
    //
    //  Если функция есть в таблице recursive_functions И содержит Заблокировать() —
    //  это потенциальный deadlock или избыточные повторные блокировки.
    // ════════════════════════════════════════════════════════════════════════════
    private static void checkRecursiveFunctionWithLock(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, Set<String> recursiveFunctionKeys,
            List<LockIssue> issues) {

        String key = objectFullName + "::" + functionName;
        if (!recursiveFunctionKeys.contains(key)) return;

        Matcher lockMatcher = MANAGED_LOCK_LOCK.matcher(functionText);
        if (!lockMatcher.find()) return;

        int lineNum = countNewlines(functionText.substring(0, lockMatcher.start())) + 1;
        issues.add(new LockIssue(
                "Блокировка в рекурсивной функции",
                "HIGH",
                "Функция является рекурсивной и содержит вызов .Заблокировать(). " +
                        "При каждом рекурсивном вызове будет попытка заблокировать те же (или пересекающиеся) данные повторно. " +
                        "В случае косвенной рекурсии (A → B → A) ветки могут блокировать пересекающиеся данные " +
                        "в разном порядке — классическая причина взаимоблокировки (deadlock). " +
                        "Чем глубже рекурсия, тем дольше висят блокировки, блокируя других пользователей.",
                functionName, objectFullName, moduleType, filePath, lineNum,
                getLine(lines, lineNum - startLine - 1),
                "Вынесите блокировку из рекурсивной функции в вызывающий код: " +
                        "установите блокировку один раз до начала рекурсии, " +
                        "рекурсивная функция должна работать уже внутри установленной блокировки. " +
                        "Если рекурсия косвенная — пересмотрите архитектуру: блокировки и рекурсия " +
                        "плохо сочетаются."
        ));
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  Вспомогательные методы
    // ════════════════════════════════════════════════════════════════════════════

    private static int countNewlines(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') count++;
        }
        return count;
    }

    private static String getLine(String[] lines, int index) {
        if (index >= 0 && index < lines.length) {
            String line = lines[index].trim();
            return line.length() > 120 ? line.substring(0, 117) + "..." : line;
        }
        return "";
    }


    private static final Pattern FOR_UPDATE_IN_QUERY = Pattern.compile(
            "ДЛЯ\\s+ИЗМЕНЕНИЯ", Pattern.CASE_INSENSITIVE);



}