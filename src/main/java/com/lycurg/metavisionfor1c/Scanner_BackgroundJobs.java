package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Анализатор проблем многопоточности в 1С:Предприятие.
 *
 * Покрывает три категории:
 *   1. Фоновые задания — дубли, цикличный запуск, параметры сеанса, ожидание завершения
 *   2. Регламентные задания — перекрытие запусков, ФЗ внутри РЗ, отсутствие обработки ошибок
 *   3. Разделяемые данные — монопольный режим, кэш на сеанс, УстановитьИсключительнуюБлокировку
 */
public class Scanner_BackgroundJobs {

    // ─────────────────────────── модель данных ───────────────────────────

    public static class BackgroundJobIssue {
        public String type;
        public String severity;       // CRITICAL / HIGH / MEDIUM / LOW / INFO
        public String category;
        public String description;
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public int    lineNumber;
        public String snippet;
        public String recommendation;

        public BackgroundJobIssue(String type, String severity, String category,
                                  String description, String functionName,
                                  String objectFullName, String moduleType,
                                  int lineNumber, String snippet, String recommendation) {
            this.type           = type;
            this.severity       = severity;
            this.category       = category;
            this.description    = description;
            this.functionName   = functionName;
            this.objectFullName = objectFullName;
            this.moduleType     = moduleType;
            this.lineNumber     = lineNumber;
            this.snippet        = snippet;
            this.recommendation = recommendation;
        }
    }

    // ─────────────────────────── паттерны ───────────────────────────────

    // ТОЧНЫЙ паттерн для вызова ФоновыеЗадания.Выполнить — только эта конструкция
    private static final Pattern P_BG_EXECUTE = Pattern.compile(
            "\\bФоновыеЗадания\\s*\\.\\s*Выполнить\\s*\\("
    );

    // Извлечение имени метода из вызова (1-й аргумент)
    private static final Pattern P_BG_METHOD_NAME = Pattern.compile(
            "ФоновыеЗадания\\s*\\.\\s*Выполнить\\s*\\(\\s*[\"']([^\"']+)[\"']"
    );

    // Проверка наличия ПолучитьФоновыеЗадания
    private static final Pattern P_BG_GET_LIST = Pattern.compile(
            "\\bФоновыеЗадания\\s*\\.\\s*ПолучитьФоновыеЗадания\\s*\\("
    );

    // Ожидание завершения
    private static final Pattern P_BG_WAIT = Pattern.compile(
            "\\bОжидатьЗавершения\\s*\\("
    );

    // Циклы (для контекстного анализа)
    private static final Pattern P_LOOP_START = Pattern.compile(
            "(?i)\\b(Для\\s+Каждого|Для\\s+\\w+|Пока\\s+|Цикл\\s*$)"
    );
    private static final Pattern P_LOOP_END = Pattern.compile(
            "(?i)\\bКонецЦикла\\b"
    );

    // Обработка ошибок
    private static final Pattern P_TRY = Pattern.compile(
            "\\bПопытка\\b"
    );

    // Параметры сеанса
    private static final Pattern P_SESSION_PARAMS = Pattern.compile(
            "\\bПараметрыСеанса\\s*\\."
    );

    // Защита от перекрытия РЗ
    private static final Pattern P_RJ_OVERLAP_CHECK = Pattern.compile(
            "\\b(?:ФоновыеЗадания\\.ПолучитьФоновыеЗадания|" +
                    "УстановитьМонопольноеЗначение|" +
                    "РегистрыСведений\\.[\\wА-яЁё]+\\.УстановитьБлокировку)\\s*\\("
    );

    // Монопольный режим
    private static final Pattern P_EXCLUSIVE_MODE = Pattern.compile(
            "\\bУстановитьМонопольныйРежим\\s*\\(\\s*Истина"
    );

    // ─────────────────────────── точка входа ─────────────────────────────

    public static List<BackgroundJobIssue> scanForBackgroundIssues() {
        List<BackgroundJobIssue> issues = new CopyOnWriteArrayList<>();

        Set<String> backgroundFunctions  = collectBackgroundFunctions();
        Set<String> scheduledFunctions   = collectScheduledFunctions();

        String sql = """
            SELECT mf.id, mf.function_name, mf.function_text, mf.start_line,
                   mm.object_full_name, mm.module_type
            FROM metadata_functions mf
            JOIN metadata_modules mm ON mf.module_id = mm.id
            WHERE mf.function_text IS NOT NULL
              AND LENGTH(mf.function_text) > 50
            """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String text         = rs.getString("function_text");
                String functionName = rs.getString("function_name");
                String objectName   = rs.getString("object_full_name");
                String moduleType   = rs.getString("module_type");
                int    startLine    = rs.getInt("start_line");
                int    functionId    = rs.getInt("id");

                String fullName = objectName + "." + functionName;

                // 🔥 КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ 1: определяем флаги ДО применения проверок
                boolean hasBgCall       = hasBackgroundJobCall(text);
                boolean isBgJob         = backgroundFunctions.contains(fullName);
                boolean isScheduledJob  = scheduledFunctions.contains(fullName)
                        || "ScheduledJobModule".equalsIgnoreCase(moduleType);

                // Если нет НИЧЕГО связанного с ФЗ — пропускаем функцию полностью
                if (!hasBgCall && !isBgJob && !isScheduledJob) {
                    continue;  // ← экономия времени и нулевые ложные срабатывания
                }

                // ── БЛОК 1: только если функция САМА является фоновым заданием ──
                if (isBgJob) {
                    checkNoErrorHandling(text, functionName, objectName, moduleType, issues, "BACKGROUND_JOB");
                    checkSessionParametersUsage(text, functionName, objectName, moduleType, issues);
                    checkNestedBackgroundJobs(text, functionName, objectName, moduleType, issues);
                    checkExclusiveModeInBackgroundJob(text, functionName, objectName, moduleType, issues);
                }

                // ── БЛОК 2: только если функция САМА является регламентным заданием ──
                if (isScheduledJob) {
                    checkNoErrorHandling(text, functionName, objectName, moduleType, issues, "SCHEDULED_JOB");
                    checkScheduledJobOverlapProtection(text, functionName, objectName, moduleType, issues);
                    checkBackgroundJobInsideScheduled(text, functionName, objectName, moduleType, issues);
                }

                // ── БЛОК 3: только если в функции ЕСТЬ вызов ФЗ ──
                if (hasBgCall) {
                    checkDuplicateJobLaunch(text, functionName, objectName, moduleType, issues, functionId);
                    checkBackgroundJobInLoop(text, functionName, objectName, moduleType, issues);
                    checkNoWaitAfterCriticalLaunch(text, functionName, objectName, moduleType, issues);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка анализа: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    /**
     * 🔥 НОВЫЙ МЕТОД: точно определяет, есть ли в тексте вызов ФоновыеЗадания.Выполнить
     * Использует строгое регулярное выражение с границами слов
     */
    private static boolean hasBackgroundJobCall(String text) {
        if (text == null) return false;
        return P_BG_EXECUTE.matcher(text).find();
    }

    // ─────────────────────── сбор функций ────────────────────────────────

    private static Set<String> collectBackgroundFunctions() {
        Set<String> result = new HashSet<>();
        String sql = """
            SELECT mf.function_text, mm.object_full_name
            FROM metadata_functions mf
            JOIN metadata_modules mm ON mf.module_id = mm.id
            WHERE mf.function_text LIKE '%ФоновыеЗадания.Выполнить%'
            """;
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String text   = rs.getString("function_text");
                Matcher m = P_BG_METHOD_NAME.matcher(text);
                while (m.find()) {
                    String methodName = m.group(1);
                    // Очищаем от возможных кавычек и пробелов
                    methodName = methodName.trim();
                    if (!methodName.isEmpty()) {
                        result.add(methodName);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ collectBackgroundFunctions: " + e.getMessage());
        }
        return result;
    }

    private static Set<String> collectScheduledFunctions() {
        Set<String> result = new HashSet<>();
        String sql = """
            SELECT method_name FROM metadata_scheduled_jobs
            WHERE method_name IS NOT NULL
            """;
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString("method_name"));
            }
        } catch (SQLException e) {
            // таблица может отсутствовать
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════════════
    // БЛОК 1 — ФОНОВЫЕ ЗАДАНИЯ
    // ══════════════════════════════════════════════════════════════════════

    private static void checkNoErrorHandling(String text, String functionName, String objectName,
                                             String moduleType,
                                             List<BackgroundJobIssue> issues, String category) {
        if (P_TRY.matcher(text).find()) return;

        String label = "BACKGROUND_JOB".equals(category) ? "фоновом задании" : "регламентном задании";
        issues.add(new BackgroundJobIssue(
                "Нет обработки ошибок",
                "HIGH",
                category,
                "В " + label + " отсутствует конструкция Попытка..Исключение. " +
                        "Необработанное исключение завершит задание без записи в журнал.",
                functionName, objectName, moduleType, 1,
                getFirstLine(text, 100),
                """
                Оберните тело задания:

                Попытка
                    // основной код
                Исключение
                    ЗаписатьЖурналРегистрации(
                        "ОписаниеЗадания",
                        УровеньЖурналаРегистрации.Ошибка,
                        , ,
                        ПодробноеПредставлениеОшибки(ИнформацияОбОшибке()));
                КонецПопытки;"""
        ));
    }

    private static void checkSessionParametersUsage(String text, String functionName, String objectName,
                                                    String moduleType,
                                                    List<BackgroundJobIssue> issues) {
        Matcher m = P_SESSION_PARAMS.matcher(text);
        if (!m.find()) return;

        int pos = m.start();
        int lineNum = countNewlines(text.substring(0, pos)) + 1;

        issues.add(new BackgroundJobIssue(
                "Использование ПараметрыСеанса в фоновом задании",
                "HIGH",
                "BACKGROUND_JOB",
                "Фоновое задание выполняется в новом сеансе. ПараметрыСеанса " +
                        "родительского сеанса туда не передаются и будут пустыми.",
                functionName, objectName, moduleType, lineNum,
                getLine(text, pos),
                """
                Передайте нужные значения явно через параметры функции:

                // Вместо:
                Пользователь = ПараметрыСеанса.ТекущийПользователь;

                // Передайте при запуске:
                Параметры = Новый Массив;
                Параметры.Добавить(ПараметрыСеанса.ТекущийПользователь);
                ФоновыеЗадания.Выполнить("МойМодуль.МояПроцедура", Параметры, ...);"""
        ));
    }

    private static void checkNestedBackgroundJobs(String text, String functionName, String objectName,
                                                  String moduleType,
                                                  List<BackgroundJobIssue> issues) {
        Matcher first = P_BG_EXECUTE.matcher(text);
        if (!first.find()) return;

        int afterFirst = first.end();
        Matcher second = P_BG_EXECUTE.matcher(text.substring(afterFirst));
        if (!second.find()) return;

        int pos = afterFirst + second.start();
        int lineNum = countNewlines(text.substring(0, pos)) + 1;

        issues.add(new BackgroundJobIssue(
                "Вложенные фоновые задания",
                "MEDIUM",
                "BACKGROUND_JOB",
                "Фоновое задание запускает другое фоновое задание.",
                functionName, objectName, moduleType, lineNum,
                getLine(text, pos),
                "Избегайте запуска ФЗ из ФЗ. Используйте планировщик или очередь в регистре сведений."
        ));
    }

    private static void checkExclusiveModeInBackgroundJob(String text, String functionName,
                                                          String objectName, String moduleType,
                                                          List<BackgroundJobIssue> issues) {
        Matcher m = P_EXCLUSIVE_MODE.matcher(text);
        if (!m.find()) return;

        int pos = m.start();
        int lineNum = countNewlines(text.substring(0, pos)) + 1;

        issues.add(new BackgroundJobIssue(
                "Монопольный режим в фоновом задании",
                "CRITICAL",
                "SHARED_DATA",
                "УстановитьМонопольныйРежим(Истина) внутри фонового задания заблокирует " +
                        "всех пользователей базы данных.",
                functionName, objectName, moduleType, lineNum,
                getLine(text, pos),
                "Используйте управляемые блокировки: БлокировкаДанных вместо монопольного режима."
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // БЛОК 2 — РЕГЛАМЕНТНЫЕ ЗАДАНИЯ
    // ══════════════════════════════════════════════════════════════════════

    private static void checkScheduledJobOverlapProtection(String text, String functionName,
                                                           String objectName, String moduleType,
                                                           List<BackgroundJobIssue> issues) {
        if (P_RJ_OVERLAP_CHECK.matcher(text).find()) return;

        issues.add(new BackgroundJobIssue(
                "Нет защиты от перекрытия регламентного задания",
                "CRITICAL",
                "SCHEDULED_JOB",
                "Регламентное задание не проверяет, запущена ли уже другая копия.",
                functionName, objectName, moduleType, 1,
                getFirstLine(text, 100),
                "Добавьте проверку через ФоновыеЗадания.ПолучитьФоновыеЗадания в начале."
        ));
    }

    private static void checkBackgroundJobInsideScheduled(String text, String functionName,
                                                          String objectName, String moduleType,
                                                          List<BackgroundJobIssue> issues) {
        Matcher m = P_BG_EXECUTE.matcher(text);
        if (!m.find()) return;

        boolean hasWait = P_BG_WAIT.matcher(text).find();
        int pos = m.start();
        int lineNum = countNewlines(text.substring(0, pos)) + 1;

        issues.add(new BackgroundJobIssue(
                hasWait ? "ФЗ внутри регламентного (с ожиданием)" : "ФЗ внутри регламентного (без ожидания)",
                hasWait ? "MEDIUM" : "HIGH",
                "SCHEDULED_JOB",
                "Регламентное задание запускает фоновое задание" +
                        (hasWait ? " и ожидает его." : " и НЕ ожидает завершения."),
                functionName, objectName, moduleType, lineNum,
                getLine(text, pos),
                hasWait ? "Убедитесь, что ожидание внутри Попытка с обработкой ошибок."
                        : "Добавьте ожидание или явно документируйте, что это стартёр."
        ));
    }

    // ══════════════════════════════════════════════════════════════════════
    // БЛОК 3 — ЛЮБЫЕ ФУНКЦИИ, ЗАПУСКАЮЩИЕ ФОНОВЫЕ ЗАДАНИЯ
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 🔥 ИСПРАВЛЕННАЯ проверка дублей
     * - Отсутствие ключа → LOW (не ошибка, просто факт)
     * - Пустой ключ → MEDIUM (хотели защиту, но не задали)
     * - Ключ в цикле → HIGH (реальная проблема)
     */
    private static void checkDuplicateJobLaunch(String text, String functionName, String objectName,
                                                String moduleType,
                                                List<BackgroundJobIssue> issues, int functionId) {

        // Если есть явная проверка — защита есть, выходим
        if (P_BG_GET_LIST.matcher(text).find()) return;

        // Проверяем, не находится ли вызов внутри цикла
        boolean insideLoop = isInsideLoop(text);

        Matcher m = P_BG_EXECUTE.matcher(text);
        while (m.find()) {
            int callStart = m.start();

            String argsRaw = extractArgs(text, m.end() - 1);
            if (argsRaw == null) continue;

            List<String> args = splitArgs(argsRaw);

            // Определяем severity на основе контекста
            String severity;
            String type;
            String recommendation;

            // Если аргументов меньше 3 — ключа нет
            if (args.size() < 3) {
                if (insideLoop) {
                    severity = "HIGH";
                    type = "ФЗ в цикле без ключа";
                    recommendation = "Добавьте ключ, иначе в цикле создастся множество дублей заданий.";
                } else {
                    // 🔥 КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ 2: отсутствие ключа — LOW
                    severity = "LOW";
                    type = "ФЗ запущено без ключа уникальности";
                    recommendation = "Если нужна защита от дублей — передайте 3-м аргументом уникальный ключ. " +
                            "Если дубли допустимы — проигнорируйте предупреждение.";
                }
            } else {
                // Аргументов 3+, проверяем не пустой ли ключ
                String keyArg = args.get(2).trim();
                boolean keyIsEmpty = keyArg.isEmpty()
                        || keyArg.equals("\"\"")
                        || keyArg.equals("''")
                        || keyArg.equalsIgnoreCase("неопределено");

                if (keyIsEmpty) {
                    severity = insideLoop ? "HIGH" : "MEDIUM";
                    type = "ФЗ запущено с пустым ключом";
                    recommendation = "Передайте реальный ключ (например, Строка(Ссылка)), чтобы защита от дублей работала.";
                } else {
                    // Ключ задан корректно — пропускаем
                    continue;
                }
            }

            int lineNumber = countNewlines(text.substring(0, callStart)) + 1;

            issues.add(new BackgroundJobIssue(
                    type,
                    severity,
                    "BACKGROUND_JOB",
                    type + ". " + getDescriptionBySeverity(severity),
                    functionName, objectName, moduleType,
                    lineNumber,
                    getLine(text, callStart),
                    recommendation
            ));
        }
    }

    /**
     * 🔥 НОВЫЙ МЕТОД: проверяет, находится ли вызов внутри цикла
     */
    private static boolean isInsideLoop(String text) {
        String[] lines = text.split("\n", -1);
        int depth = 0;

        for (String line : lines) {
            if (P_LOOP_START.matcher(line).find()) {
                depth++;
            }
            if (P_LOOP_END.matcher(line).find() && depth > 0) {
                depth--;
            }
            if (depth > 0 && P_BG_EXECUTE.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static String getDescriptionBySeverity(String severity) {
        switch (severity) {
            case "HIGH": return "Запуск в цикле без ключа — гарантированно создаст множество дублей.";
            case "MEDIUM": return "Ключ передан, но пустой — защита от дублей не работает.";
            default: return "Ключ не передан. Если нужна защита от дублей — добавьте 3-й аргумент.";
        }
    }

    private static void checkBackgroundJobInLoop(String text, String functionName, String objectName,
                                                 String moduleType,
                                                 List<BackgroundJobIssue> issues) {
        // Проверяем только если есть вызов внутри цикла И это не было уже покрыто checkDuplicateJobLaunch
        if (!isInsideLoop(text)) return;

        String[] lines = text.split("\n", -1);
        int depth = 0;
        int loopStartLine = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (P_LOOP_START.matcher(line).find()) {
                if (depth == 0) loopStartLine = i;
                depth++;
            }
            if (P_LOOP_END.matcher(line).find() && depth > 0) {
                depth--;
            }
            if (depth > 0 && P_BG_EXECUTE.matcher(line).find()) {
                int absLine = i + 1;
                issues.add(new BackgroundJobIssue(
                        "Массовый запуск ФЗ в цикле",
                        "HIGH",
                        "BACKGROUND_JOB",
                        "ФоновыеЗадания.Выполнить вызывается внутри цикла (начало — строка " +
                                (loopStartLine + 1) + "). При большом объёме данных создастся множество заданий.",
                        functionName, objectName, moduleType,
                        absLine,
                        line.trim().length() > 120 ? line.trim().substring(0, 117) + "..." : line.trim(),
                        "Используйте батчевую обработку: разбейте данные на пакеты по 50-200 элементов."
                ));
                break;
            }
        }
    }

    private static void checkNoWaitAfterCriticalLaunch(String text, String functionName,
                                                       String objectName, String moduleType,
                                                       List<BackgroundJobIssue> issues) {
        // Проверяем только если результат присваивается переменной
        Pattern assignedLaunch = Pattern.compile(
                "\\b\\w+\\s*=\\s*ФоновыеЗадания\\s*\\.\\s*Выполнить\\s*\\("
        );

        if (!assignedLaunch.matcher(text).find()) return;
        if (P_BG_WAIT.matcher(text).find()) return;

        Matcher m = assignedLaunch.matcher(text);
        if (m.find()) {
            int pos = m.start();
            int lineNum = countNewlines(text.substring(0, pos)) + 1;

            issues.add(new BackgroundJobIssue(
                    "ФЗ запущено без ожидания результата",
                    "LOW",
                    "BACKGROUND_JOB",
                    "Результат ФоновыеЗадания.Выполнить сохраняется, но ОжидатьЗавершения не вызывается.",
                    functionName, objectName, moduleType, lineNum,
                    getLine(text, pos),
                    "Если результат нужен — добавьте ОжидатьЗавершения. Если нет — уберите присваивание."
            ));
        }
    }

    // ─────────────────────────── утилиты ─────────────────────────────────

    private static int countNewlines(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') count++;
        }
        return count;
    }

    private static String getLine(String text, int charPos) {
        String[] lines = text.split("\n", -1);
        int idx = countNewlines(text.substring(0, Math.min(charPos, text.length())));
        if (idx < lines.length) {
            String line = lines[idx].trim();
            return line.length() > 120 ? line.substring(0, 117) + "..." : line;
        }
        return "";
    }

    private static String getFirstLine(String text, int maxLength) {
        String line = text.split("\n", -1)[0].trim();
        return line.length() > maxLength ? line.substring(0, maxLength - 3) + "..." : line;
    }

    private static String extractArgs(String text, int openParenPos) {
        if (openParenPos >= text.length() || text.charAt(openParenPos) != '(') return null;

        int depth = 0;
        int start = openParenPos + 1;
        for (int i = openParenPos; i < Math.min(text.length(), openParenPos + 2000); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return text.substring(start, i);
            }
        }
        return null;
    }

    private static List<String> splitArgs(String argsRaw) {
        List<String> result = new ArrayList<>();
        int depth  = 0;
        boolean inStr = false;
        char strChar  = 0;
        int start     = 0;

        for (int i = 0; i < argsRaw.length(); i++) {
            char c = argsRaw.charAt(i);

            if (!inStr && (c == '"' || c == '\'')) {
                inStr   = true;
                strChar = c;
            } else if (inStr && c == strChar) {
                inStr = false;
            } else if (!inStr) {
                if      (c == '(' || c == '[') depth++;
                else if (c == ')' || c == ']') depth--;
                else if (c == ',' && depth == 0) {
                    result.add(argsRaw.substring(start, i));
                    start = i + 1;
                }
            }
        }
        result.add(argsRaw.substring(start));
        return result;
    }

    // ─────────────────────────── статистика ──────────────────────────────

    public static Map<String, Object> getStatistics(List<BackgroundJobIssue> issues) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("TOTAL", issues.size());

        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        bySeverity.put("CRITICAL", 0);
        bySeverity.put("HIGH", 0);
        bySeverity.put("MEDIUM", 0);
        bySeverity.put("LOW", 0);

        Map<String, Integer> byCategory = new LinkedHashMap<>();
        byCategory.put("BACKGROUND_JOB", 0);
        byCategory.put("SCHEDULED_JOB", 0);
        byCategory.put("SHARED_DATA", 0);

        for (BackgroundJobIssue issue : issues) {
            bySeverity.merge(issue.severity, 1, Integer::sum);
            byCategory.merge(issue.category, 1, Integer::sum);
        }

        stats.put("BY_SEVERITY", bySeverity);
        stats.put("BY_CATEGORY", byCategory);
        return stats;
    }
}