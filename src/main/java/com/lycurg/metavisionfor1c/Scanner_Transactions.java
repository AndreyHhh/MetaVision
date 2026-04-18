package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class Scanner_Transactions {

    public static class TransactionIssue {
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

        public TransactionIssue(String type, String severity, String description,
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

    // ── Транзакции ───────────────────────────────────────────────────────────
    private static final Pattern BEGIN_TRANSACTION = Pattern.compile(
            "НачатьТранзакцию\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMIT_TRANSACTION = Pattern.compile(
            "ЗафиксироватьТранзакцию\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROLLBACK_TRANSACTION = Pattern.compile(
            "ОтменитьТранзакцию\\s*\\(", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRANSACTION_ACTIVE = Pattern.compile(
            "ТранзакцияАктивна\\s*\\(", Pattern.CASE_INSENSITIVE);

    // ── Попытка..Исключение..КонецПопытки — нежадный матч ───────────────────
    // Ищем каждый блок отдельно чтобы не схлопывать несколько блоков в один
    private static final Pattern TRY_BLOCK = Pattern.compile(
            "Попытка(.*?)Исключение(.*?)КонецПопытки",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // ── Диалоги с пользователем ──────────────────────────────────────────────
    private static final Pattern DIALOG = Pattern.compile(
            "(Вопрос\\s*\\(|" +
                    "Предупреждение\\s*\\(|" +
                    "ОткрытьФормуМодально\\s*\\(|" +
                    "ПоказатьВопрос\\s*\\(|" +
                    "ПоказатьПредупреждение\\s*\\(|" +
                    "ВвестиСтроку\\s*\\(|" +
                    "ВвестиДату\\s*\\(|" +
                    "ВвестиЧисло\\s*\\(|" +
                    "ВыбратьИзСписка\\s*\\()",
            Pattern.CASE_INSENSITIVE);

    // ── ВызватьИсключение ────────────────────────────────────────────────────
    private static final Pattern RAISE_EXCEPTION = Pattern.compile(
            "ВызватьИсключение", Pattern.CASE_INSENSITIVE);

    // ════════════════════════════════════════════════════════════════════════
    //  ТОЧКА ВХОДА
    // ════════════════════════════════════════════════════════════════════════
    public static List<TransactionIssue> scanForTransactionIssues() {
        List<TransactionIssue> issues = new CopyOnWriteArrayList<>();

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
              AND mf.function_text LIKE '%НачатьТранзакцию%'
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

                analyzeFunctionForTransactionIssues(
                        functionName, functionText, objectFullName,
                        moduleType, filePath, startLine,
                        recursiveFunctionKeys, issues);
            }

            System.out.println("Найдено проблем с транзакциями: " + issues.size());

        } catch (SQLException e) {
            System.err.println("Ошибка анализа транзакций: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Загрузка рекурсивных функций
    // ════════════════════════════════════════════════════════════════════════
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
            System.err.println("Предупреждение: не удалось загрузить recursive_functions: " + e.getMessage());
        }
        return keys;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  ДИСПЕТЧЕР ПРОВЕРОК
    // ════════════════════════════════════════════════════════════════════════
    private static void analyzeFunctionForTransactionIssues(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            Set<String> recursiveFunctionKeys,
            List<TransactionIssue> issues) {

        String[] lines = functionText.split("\n");
        List<int[]> tryRanges = collectTryRanges(functionText);

        // 1. НачатьТранзакцию вне блока Попытка..КонецПопытки
        checkNoTryCatch(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines, tryRanges, issues);

        // 2. НачатьТранзакцию без ЗафиксироватьТранзакцию (LOW — фиксация может быть в вызываемой функции)
        checkBeginWithoutCommit(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines, issues);

        // 3. Нет ОтменитьТранзакцию в блоке Исключение (объединяет старые проверки 3 и 4)
        checkRollbackInException(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines, issues);

        // 4. Диалог с пользователем внутри транзакции
        checkDialogsInsideTransaction(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines, issues);

        // 5. Вложенные транзакции без проверки ТранзакцияАктивна()
        checkNestedWithoutActiveCheck(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines, issues);

        // 6. Рекурсивная функция с транзакцией
        checkRecursiveFunctionWithTransaction(functionName, functionText, objectFullName,
                moduleType, filePath, startLine, lines,
                recursiveFunctionKeys, issues);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  1. НачатьТранзакцию вне блока Попытка..КонецПопытки
    // ════════════════════════════════════════════════════════════════════════
    private static void checkNoTryCatch(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, List<int[]> tryRanges, List<TransactionIssue> issues) {

        Matcher beginMatcher = BEGIN_TRANSACTION.matcher(functionText);
        while (beginMatcher.find()) {
            int pos = beginMatcher.start();
            if (!isInsideAnyTryBlock(pos, tryRanges)) {
                int lineNum = countNewlines(functionText.substring(0, pos)) + 1;
                issues.add(new TransactionIssue(
                        "Транзакция без обработки ошибок",
                        "HIGH",
                        "НачатьТранзакцию() находится вне блока Попытка..КонецПопытки. " +
                                "Если в процессе выполнения произойдёт любая ошибка — исключение уйдёт " +
                                "наверх, транзакция не будет отменена, данные останутся заблокированными " +
                                "до таймаута или перезапуска сеанса.",
                        functionName, objectFullName, moduleType, filePath, lineNum,
                        getLine(lines, lineNum - startLine - 1),
                        "Оберните всю транзакцию в Попытка..КонецПопытки:\n" +
                                "    Попытка\n" +
                                "        НачатьТранзакцию();\n" +
                                "        // рабочий код\n" +
                                "        ЗафиксироватьТранзакцию();\n" +
                                "    Исключение\n" +
                                "        ОтменитьТранзакцию();\n" +
                                "        ВызватьИсключение;\n" +
                                "    КонецПопытки;"
                ));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  2. НачатьТранзакцию без ЗафиксироватьТранзакцию (LOW)
    //
    //  Не критично — фиксация может быть в вызываемой функции.
    //  Сигнал для ревью, не для исправления.
    // ════════════════════════════════════════════════════════════════════════
    private static void checkBeginWithoutCommit(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, List<TransactionIssue> issues) {

        if (!BEGIN_TRANSACTION.matcher(functionText).find()) return;
        if (COMMIT_TRANSACTION.matcher(functionText).find()) return;
        // Если есть ОтменитьТранзакцию — возможно намеренный rollback, не сообщаем
        if (ROLLBACK_TRANSACTION.matcher(functionText).find()) return;

        Matcher m = BEGIN_TRANSACTION.matcher(functionText);
        if (m.find()) {
            int lineNum = countNewlines(functionText.substring(0, m.start())) + 1;
            issues.add(new TransactionIssue(
                    "Транзакция без явной фиксации в функции",
                    "LOW",
                    "НачатьТранзакцию() вызывается, но ЗафиксироватьТранзакцию() " +
                            "в этой функции не найден. Возможно, фиксация вынесена в вызывающий код — " +
                            "это допустимо, но усложняет понимание жизненного цикла транзакции.",
                    functionName, objectFullName, moduleType, filePath, lineNum,
                    getLine(lines, lineNum - startLine - 1),
                    "Проверьте, что вызывающий код гарантированно завершает транзакцию " +
                            "через ЗафиксироватьТранзакцию() или ОтменитьТранзакцию() во всех ветках. " +
                            "Если возможно — держите открытие и закрытие транзакции в одной функции."
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  3. Нет ОтменитьТранзакцию в блоке Исключение
    //
    //  Объединяет два случая:
    //  а) просто нет ОтменитьТранзакцию
    //  б) есть ВызватьИсключение но нет ОтменитьТранзакцию перед ним
    //
    //  Оба случая одинаково опасны — транзакция уходит наверх незакрытой.
    //  Одно сообщение на блок Попытка, без дублирования.
    // ════════════════════════════════════════════════════════════════════════
    private static void checkRollbackInException(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, List<TransactionIssue> issues) {

        // Ищем каждый блок Попытка..КонецПопытки отдельно
        Matcher tryMatcher = TRY_BLOCK.matcher(functionText);
        while (tryMatcher.find()) {
            String tryBlock       = tryMatcher.group(1);
            String exceptionBlock = tryMatcher.group(2);

            // Нас интересует только блок где открыта транзакция
            if (!BEGIN_TRANSACTION.matcher(tryBlock).find()) continue;

            // Если ОтменитьТранзакцию есть — всё хорошо
            if (ROLLBACK_TRANSACTION.matcher(exceptionBlock).find()) continue;

            boolean hasRaise = RAISE_EXCEPTION.matcher(exceptionBlock).find();

            int lineNum = countNewlines(functionText.substring(0, tryMatcher.start())) + 1;
            String description = hasRaise
                    ? "В блоке Исключение есть ВызватьИсключение, но отсутствует ОтменитьТранзакцию(). " +
                    "Исключение уйдёт наверх, транзакция останется незакрытой — " +
                    "платформа не отменяет её автоматически при передаче исключения."
                    : "Транзакция открыта в блоке Попытка, но в блоке Исключение " +
                    "отсутствует ОтменитьТранзакцию(). При любой ошибке транзакция " +
                    "зависнет и будет блокировать данные до таймаута сеанса.";

            issues.add(new TransactionIssue(
                    "Нет ОтменитьТранзакцию() в блоке Исключение",
                    "CRITICAL",
                    description,
                    functionName, objectFullName, moduleType, filePath, lineNum,
                    getLine(lines, lineNum - startLine - 1),
                    "В блоке Исключение всегда должен быть ОтменитьТранзакцию() первой строкой:\n" +
                            "    Исключение\n" +
                            "        ОтменитьТранзакцию();\n" +
                            "        // запись в журнал, обработка ошибки\n" +
                            "        ВызватьИсключение;\n" +
                            "    КонецПопытки;\n" +
                            "Вызов ОтменитьТранзакцию() на уже отменённой транзакции безопасен — " +
                            "платформа это допускает."
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  4. Диалог с пользователем внутри транзакции
    // ════════════════════════════════════════════════════════════════════════
    private static void checkDialogsInsideTransaction(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, List<TransactionIssue> issues) {

        Matcher beginMatcher = BEGIN_TRANSACTION.matcher(functionText);
        while (beginMatcher.find()) {
            int beginPos = beginMatcher.start();
            int endPos   = findEndOfTransaction(functionText, beginPos);
            if (endPos == -1) continue;

            String body = functionText.substring(beginPos, endPos);
            Matcher dialogMatcher = DIALOG.matcher(body);
            if (dialogMatcher.find()) {
                int dialogLine = countNewlines(functionText.substring(0, beginPos))
                        + countNewlines(body.substring(0, dialogMatcher.start())) + 1;
                issues.add(new TransactionIssue(
                        "Диалог с пользователем внутри транзакции",
                        "CRITICAL",
                        "Внутри транзакции обнаружен диалог: " + dialogMatcher.group().trim() + ". " +
                                "Пока пользователь думает над ответом — транзакция открыта, " +
                                "все заблокированные данные недоступны другим пользователям. " +
                                "Это может быть секунда, а может быть час.",
                        functionName, objectFullName, moduleType, filePath, dialogLine,
                        getLine(lines, dialogLine - startLine - 1),
                        "Вынесите все диалоги за пределы транзакции. " +
                                "Задайте вопросы пользователю до НачатьТранзакцию(), " +
                                "сохраните ответы в переменные, используйте их внутри транзакции. " +
                                "Транзакция должна быть максимально короткой — только запись данных."
                ));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  5. Вложенные транзакции без проверки ТранзакцияАктивна()
    // ════════════════════════════════════════════════════════════════════════
    private static void checkNestedWithoutActiveCheck(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, List<TransactionIssue> issues) {

        if (TRANSACTION_ACTIVE.matcher(functionText).find()) return;

        Matcher beginMatcher = BEGIN_TRANSACTION.matcher(functionText);
        int count = 0;
        int firstPos = -1;
        while (beginMatcher.find()) {
            count++;
            if (firstPos == -1) firstPos = beginMatcher.start();
        }

        if (count >= 2 && firstPos != -1) {
            int lineNum = countNewlines(functionText.substring(0, firstPos)) + 1;
            issues.add(new TransactionIssue(
                    "Вложенные транзакции без ТранзакцияАктивна()",
                    "MEDIUM",
                    "Функция содержит " + count + " вызова НачатьТранзакцию() без проверки " +
                            "ТранзакцияАктивна(). В 1С транзакции считаются счётчиком: каждый " +
                            "НачатьТранзакцию() увеличивает счётчик, каждый ЗафиксироватьТранзакцию() " +
                            "уменьшает. Реальная фиксация происходит только когда счётчик достигает нуля. " +
                            "Непреднамеренное вложение искажает этот счётчик.",
                    functionName, objectFullName, moduleType, filePath, lineNum,
                    getLine(lines, lineNum - startLine - 1),
                    "Если вложение намеренно — добавьте проверку перед каждым НачатьТранзакцию():\n" +
                            "    Если НЕ ТранзакцияАктивна() Тогда\n" +
                            "        НачатьТранзакцию();\n" +
                            "    КонецЕсли;\n" +
                            "Если вложение случайное — пересмотрите логику, оставьте один НачатьТранзакцию()."
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  6. Рекурсивная функция с транзакцией
    // ════════════════════════════════════════════════════════════════════════
    private static void checkRecursiveFunctionWithTransaction(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            String[] lines, Set<String> recursiveFunctionKeys,
            List<TransactionIssue> issues) {

        String key = objectFullName + "::" + functionName;
        if (!recursiveFunctionKeys.contains(key)) return;
        if (!BEGIN_TRANSACTION.matcher(functionText).find()) return;

        Matcher m = BEGIN_TRANSACTION.matcher(functionText);
        if (m.find()) {
            int lineNum = countNewlines(functionText.substring(0, m.start())) + 1;
            issues.add(new TransactionIssue(
                    "Транзакция в рекурсивной функции",
                    "HIGH",
                    "Функция является рекурсивной и содержит НачатьТранзакцию(). " +
                            "При каждом рекурсивном вызове счётчик вложенности транзакций увеличивается. " +
                            "Реальная фиксация произойдёт только на самом верхнем уровне рекурсии — " +
                            "это значит все промежуточные уровни держат незафиксированные данные. " +
                            "При косвенной рекурсии (A → B → A) ситуация ещё хуже: " +
                            "транзакции могут конфликтовать и вызывать взаимоблокировку.",
                    functionName, objectFullName, moduleType, filePath, lineNum,
                    getLine(lines, lineNum - startLine - 1),
                    "Вынесите транзакцию из рекурсивной функции в вызывающий код: " +
                            "откройте транзакцию один раз до запуска рекурсии, " +
                            "рекурсивная функция должна работать уже внутри открытой транзакции. " +
                            "Если это невозможно — добавьте проверку ТранзакцияАктивна() " +
                            "и не открывайте новую если транзакция уже есть."
            ));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Вспомогательные методы
    // ════════════════════════════════════════════════════════════════════════

    private static List<int[]> collectTryRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        Matcher m = TRY_BLOCK.matcher(text);
        while (m.find()) {
            ranges.add(new int[]{m.start(), m.end()});
        }
        return ranges;
    }

    private static boolean isInsideAnyTryBlock(int pos, List<int[]> tryRanges) {
        for (int[] range : tryRanges) {
            if (pos >= range[0] && pos <= range[1]) return true;
        }
        return false;
    }

    private static int findEndOfTransaction(String text, int beginPos) {
        int commitPos   = findFirstAfter(COMMIT_TRANSACTION,   text, beginPos);
        int rollbackPos = findFirstAfter(ROLLBACK_TRANSACTION, text, beginPos);
        if (commitPos == -1 && rollbackPos == -1) return -1;
        if (commitPos == -1)   return rollbackPos;
        if (rollbackPos == -1) return commitPos;
        return Math.min(commitPos, rollbackPos);
    }

    private static int findFirstAfter(Pattern pattern, String text, int afterPos) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (m.start() > afterPos) return m.start();
        }
        return -1;
    }

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
}