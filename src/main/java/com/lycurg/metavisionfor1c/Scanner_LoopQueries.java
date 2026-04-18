package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class Scanner_LoopQueries {
    // В начале класса добавляем кэш рекурсивных функций
    private static Set<Integer> recursiveFunctionIds = null;

    /**
     * Загружает ID всех рекурсивных функций из таблицы recursive_functions
     */
    private static Set<Integer> getRecursiveFunctionIds() {
        if (recursiveFunctionIds != null) return recursiveFunctionIds;

        recursiveFunctionIds = new HashSet<>();
        String sql = "SELECT DISTINCT function_id FROM recursive_functions";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                recursiveFunctionIds.add(rs.getInt("function_id"));
            }
        } catch (SQLException e) {
            // Таблицы может не быть — игнорируем
            System.err.println("⚠️ Таблица recursive_functions не найдена: " + e.getMessage());
        }
        return recursiveFunctionIds;
    }

    /**
     * Проверяет, является ли функция рекурсивной
     */
    private static boolean isRecursiveFunction(int functionId) {
        return getRecursiveFunctionIds().contains(functionId);
    }


    public static class LoopQueryIssue {
        public String type;
        public String severity;
        public String description;
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public String snippet;
        public int lineNumber;
        public String recommendation;
        public String modulePath;
        public int errorLine;

        public LoopQueryIssue(String type, String severity, String description,
                              String functionName, String objectFullName, String moduleType,
                              String snippet, int lineNumber, String recommendation,
                              String modulePath, int errorLine) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.functionName = functionName;
            this.objectFullName = objectFullName;
            this.moduleType = moduleType;
            this.snippet = snippet;
            this.lineNumber = lineNumber;
            this.recommendation = recommendation;
            this.modulePath = modulePath;
            this.errorLine = errorLine;
        }
    }

    private static final Pattern PATTERN_FIND_BY_NAME = Pattern.compile(
            "(?i)(НайтиПоНаименованию|НайтиПоКоду)\\s*\\(",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern PATTERN_STRING_LITERAL = Pattern.compile("\"[^\"]*\"");

    private static final Pattern PATTERN_LOOP_HEADER_FOREACH = Pattern.compile(
            "(?i)Для\\s+Каждого\\s+\\S+\\s+Из\\s+.+?\\s+Цикл",
            Pattern.UNICODE_CASE
    );

    private static final Pattern PATTERN_LOOP_HEADER_NUMERIC = Pattern.compile(
            "(?i)Для\\s+\\S+\\s*=\\s*.+?\\s+По\\s+.+?\\s+Цикл",
            Pattern.UNICODE_CASE
    );

    private static final Pattern PATTERN_LOOP_HEADER_WHILE = Pattern.compile(
            "(?i)Пока\\s+.+?\\s+Цикл",
            Pattern.UNICODE_CASE
    );

    // Типы метаданных, которые кешируются и не вызывают запросов к БД (в верхнем регистре)
    private static final Set<String> CACHED_METADATA_TYPES = Set.of(
            "ПЕРЕЧИСЛЕНИЯ", "КОНСТАНТЫ", "ПЕРЕЧИСЛЕНИЯССЫЛКА", "КОНСТАНТЫССЫЛКА",
            "ПЕРЕЧИСЛЕНИЕ", "КОНСТАНТА", "ПАРАМЕТРЫ", "МЕТАДАННЫЕ","ОБМЕНДАННЫМИ"
    );

    public static List<LoopQueryIssue> scanForLoopQueries() {
        List<LoopQueryIssue> issues = new ArrayList<>();

        String sql = """
        SELECT 
            ce.id,
            ce.start_line,
            ce.end_line,
            ce.function_id,
            mf.function_name,
            mf.function_text,
            mm.object_full_name,
            mm.module_type,
            mm.file_path
        FROM code_elements ce
        JOIN metadata_functions mf ON ce.function_id = mf.id
        JOIN metadata_modules mm ON mf.module_id = mm.id
        WHERE ce.element_type IN ('ЦиклНезависимый', 'ЦиклЗапроса')
          AND ce.start_line IS NOT NULL
          AND ce.end_line IS NOT NULL
        ORDER BY mm.object_full_name, mf.function_name, ce.start_line
    """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String fullFunctionText = rs.getString("function_text");
                int cycleStartLine = rs.getInt("start_line");
                int cycleEndLine = rs.getInt("end_line");
                String functionName = rs.getString("function_name");
                String objectFullName = rs.getString("object_full_name");
                String moduleType = rs.getString("module_type");
                String modulePath = rs.getString("file_path");

                if (fullFunctionText == null || fullFunctionText.isEmpty()) continue;

                // Пробуем извлечь тело цикла
                String cycleBody = extractCycleBodySafe(fullFunctionText, cycleStartLine, cycleEndLine);
                if (cycleBody == null || cycleBody.isEmpty()) continue;

                analyzeCycle(cycleBody, functionName, objectFullName, moduleType,
                        cycleStartLine, modulePath, issues);
            }

        } catch (SQLException e) {
            System.err.println("❌ Ошибка сканирования циклов: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    private static String extractCycleBodySafe(String functionText, int cycleStartLine, int cycleEndLine) {
        String[] lines = functionText.split("\n", -1);

        // Просто проверяем, что строки существуют
        if (cycleStartLine < 1 || cycleEndLine > lines.length) {
            return null;
        }

        int startIdx = cycleStartLine - 1;
        int endIdx = cycleEndLine - 1;

        if (startIdx > endIdx) return null;

        String startLineText = lines[startIdx];
        int cyclePos = startLineText.toLowerCase().indexOf("цикл");
        if (cyclePos == -1) return null;

        String firstLine = startLineText.substring(cyclePos + 4);

        StringBuilder body = new StringBuilder();
        body.append(firstLine).append("\n");

        for (int i = startIdx + 1; i <= endIdx; i++) {
            body.append(lines[i]).append("\n");
        }

        return body.toString().trim();
    }

    private static String extractCycleBody(String functionText, int cycleStartLine, int cycleEndLine, int funcStartLine) {
        String[] lines = functionText.split("\n", -1);

        // Проверяем границы
        if (cycleStartLine < funcStartLine || cycleEndLine > lines.length) {
            return null;
        }

        // Смещаем индексы относительно начала функции
        int startIdx = cycleStartLine - funcStartLine;
        int endIdx = cycleEndLine - funcStartLine;

        if (startIdx < 0 || endIdx >= lines.length || startIdx > endIdx) {
            return null;
        }

        String startLineText = lines[startIdx];
        int cyclePos = startLineText.toLowerCase().indexOf("цикл");
        if (cyclePos == -1) return null;

        // Извлекаем тело цикла
        String firstLine = startLineText.substring(cyclePos + 4);

        StringBuilder body = new StringBuilder();
        body.append(firstLine).append("\n");

        for (int i = startIdx + 1; i <= endIdx; i++) {
            body.append(lines[i]).append("\n");
        }

        return body.toString().trim();
    }



    private static String extractCycleBody(String functionText, int startLine, int endLine) {
        String[] lines = functionText.split("\n", -1);
        if (startLine < 1 || endLine > lines.length) return null;

        int startIdx = startLine - 1;
        int endIdx = endLine - 1;

        String startLineText = lines[startIdx];
        int cyclePos = startLineText.indexOf("Цикл");
        if (cyclePos == -1) return null;

        String firstLine = startLineText.substring(cyclePos + 4);

        StringBuilder body = new StringBuilder();
        body.append(firstLine).append("\n");

        for (int i = startIdx + 1; i < endIdx; i++) {
            body.append(lines[i]).append("\n");
        }

        return body.toString().trim();
    }

    private static boolean containsCachedMetadataType(String fullChain) {
        if (fullChain == null) return false;
        String upperChain = fullChain.toUpperCase();
        for (String type : CACHED_METADATA_TYPES) {
            if (upperChain.contains(type)) {
                return true;
            }
        }
        return false;
    }

    private static int countDots(String chain) {
        int count = 0;
        for (int i = 0; i < chain.length(); i++) {
            if (chain.charAt(i) == '.') count++;
        }
        return count;
    }

    private static String getSeverityByDots(int dotCount) {
        switch (dotCount) {
            case 2: return "LOW";
            case 3: return "MEDIUM";
            case 4: return "HIGH";
            default: return "CRITICAL";
        }
    }

    private static String getDescriptionByDots(int dotCount, String fullAccess) {
        if (dotCount == 2) {
            return "Обращение к реквизиту второго уровня " + fullAccess + " внутри цикла.";
        } else if (dotCount == 3) {
            return "Обращение к реквизиту третьего уровня " + fullAccess + " внутри цикла — увеличивает нагрузку на БД.";
        } else if (dotCount == 4) {
            return "Глубокое обращение (4 уровня) " + fullAccess + " внутри цикла — значительная нагрузка на БД.";
        } else {
            return "Критически глубокое обращение (" + dotCount + " уровней) " + fullAccess + " внутри цикла — многократные запросы к БД на каждой итерации.";
        }
    }

    private static boolean isInsideQuery(String text, int position) {
        boolean inQuote = false;
        for (int i = 0; i < position; i++) {
            if (text.charAt(i) == '"') {
                if (i > 0 && text.charAt(i-1) == '\\') continue;
                inQuote = !inQuote;
            }
        }
        return inQuote;
    }

    private static boolean isLinePartOfQuery(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|") ||
                trimmed.startsWith("ВЫБРАТЬ") ||
                trimmed.startsWith("ИЗ") ||
                trimmed.startsWith("КАК") ||
                trimmed.contains(" КАК ") ||
                (trimmed.contains("|") && trimmed.contains("КАК"))) {
            return true;
        }
        return false;
    }

    private static boolean isMethodCallAfter(String line, int endPos) {
        int tempPos = endPos;
        while (tempPos < line.length() && Character.isWhitespace(line.charAt(tempPos))) {
            tempPos++;
        }
        return tempPos < line.length() && line.charAt(tempPos) == '(';
    }

    private static void analyzeCycle(String cycleBody, String functionName,
                                     String objectFullName, String moduleType,
                                     int cycleStartLine, String modulePath,
                                     List<LoopQueryIssue> issues) {

        // Получаем ID текущей функции (нужно для проверки рекурсии)
        int currentFunctionId = getFunctionIdByName(functionName, objectFullName);
        boolean isRecursive = isRecursiveFunction(currentFunctionId);

        String strippedText = PATTERN_STRING_LITERAL.matcher(cycleBody).replaceAll("\"\"");
        List<int[]> headerRanges = collectLoopHeaderRanges(strippedText);

        // ========== 1. ПОИСК НайтиПоНаименованию / НайтиПоКоду ==========
        Matcher findMatcher = PATTERN_FIND_BY_NAME.matcher(cycleBody);
        while (findMatcher.find()) {
            String findType = findMatcher.group(1);

            if (isInsideQuery(cycleBody, findMatcher.start())) continue;
            if (isInsideHeaderRange(findMatcher.start(), headerRanges)) continue;

            int lineOffset = getLineNumberInBody(cycleBody, findMatcher.start(), cycleStartLine);
            String snippet = extractSnippet(cycleBody, findMatcher.start(), 100);

            // Определяем severity с учётом рекурсии
            String severity = isRecursive ? "CRITICAL" : "CRITICAL";
            String description = isRecursive
                    ? "[РЕКУРСИВНАЯ ФУНКЦИЯ!] Вызов " + findType + "() внутри цикла в рекурсивной функции — катастрофический рост числа запросов."
                    : "Вызов " + findType + "() внутри цикла — каждая итерация выполняет запрос к БД.";

            String recommendation = isRecursive
                    ? getFindRecommendationWithRecursion(findType)
                    : getFindRecommendation(findType);

            issues.add(new LoopQueryIssue(
                    findType,
                    severity,
                    description,
                    functionName,
                    objectFullName,
                    moduleType,
                    snippet,
                    lineOffset,
                    recommendation,
                    modulePath,
                    lineOffset
            ));
        }

        // ========== 2. ПОИСК ГЛУБОКИХ ОБРАЩЕНИЙ (2+ точки) ==========
        String[] lines = cycleBody.split("\n");
        Set<String> foundAccesses = new HashSet<>();

        Pattern chainPattern = Pattern.compile(
                "\\b([А-Яа-яA-Za-z_][А-Яа-яA-Za-z0-9_]*\\s*\\.\\s*[А-Яа-яA-Za-z_][А-Яа-яA-Za-z0-9_]+\\s*\\.\\s*[А-Яа-яA-Za-z_][А-Яа-яA-Za-z0-9_]+(?:\\s*\\.\\s*[А-Яа-яA-Za-z_][А-Яа-яA-Za-z0-9_]+)*)\\b",
                Pattern.UNICODE_CASE
        );

        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            String line = lines[lineIdx];

            if (isLinePartOfQuery(line)) continue;

            String strippedLine = PATTERN_STRING_LITERAL.matcher(line).replaceAll("\"\"");
            Matcher chainMatcher = chainPattern.matcher(strippedLine);

            while (chainMatcher.find()) {
                String fullChain = chainMatcher.group(1);
                int dotCount = countDots(fullChain);
                if (dotCount < 2) continue;
                if (containsCachedMetadataType(fullChain)) continue;
                if (isMethodCallAfter(strippedLine, chainMatcher.end())) continue;

                int originalPos = findPositionInOriginal(line, fullChain);
                if (originalPos == -1) continue;

                int globalPos = getGlobalPosition(cycleBody, lineIdx, originalPos);
                if (isInsideHeaderRange(globalPos, headerRanges)) continue;

                String key = fullChain + "|" + lineIdx;
                if (foundAccesses.contains(key)) continue;
                foundAccesses.add(key);

                int errorLine = cycleStartLine + lineIdx + 1;
                String snippet = extractSnippetFromLine(line, originalPos, 80);

                // Определяем severity с учётом рекурсии
                String baseSeverity = getSeverityByDots(dotCount);
                String severity = isRecursive ? "CRITICAL" : baseSeverity;

                String baseDescription = getDescriptionByDots(dotCount, fullChain);
                String description = isRecursive
                        ? "[РЕКУРСИВНАЯ ФУНКЦИЯ!] " + baseDescription + " В рекурсивной функции проблема многократно усиливается."
                        : baseDescription;

                String recommendation = isRecursive
                        ? getDotAccessRecommendationWithRecursion(fullChain, dotCount)
                        : getDotAccessRecommendation(fullChain, dotCount);

                issues.add(new LoopQueryIssue(
                        "Доступ к реквизиту (" + dotCount + " уровня)",
                        severity,
                        description,
                        functionName,
                        objectFullName,
                        moduleType,
                        snippet,
                        errorLine,
                        recommendation,
                        modulePath,
                        errorLine
                ));
            }
        }
    }


    /**
     * Возвращает ID функции по имени и имени объекта
     */
    private static int getFunctionIdByName(String functionName, String objectFullName) {
        String sql = """
        SELECT mf.id FROM metadata_functions mf
        JOIN metadata_modules mm ON mf.module_id = mm.id
        WHERE mf.function_name = ? AND mm.object_full_name = ?
    """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, functionName);
            ps.setString(2, objectFullName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            // Игнорируем
        }
        return -1;
    }




    /**
     * Рекомендация для НайтиПоНаименованию с учётом рекурсии
     */
    private static String getFindRecommendationWithRecursion(String findType) {
        return "🔴🔴 КРИТИЧЕСКАЯ ПРОБЛЕМА: РЕКУРСИЯ + ЗАПРОС В ЦИКЛЕ 🔴🔴\n\n" +
                "Это катастрофическая комбинация! Каждый уровень рекурсии умножает количество запросов.\n\n" +
                "НЕОБХОДИМО полное перепроектирование:\n" +
                "1. Преобразуйте рекурсию в итеративный алгоритм\n" +
                "2. Вынесите поиск элементов за пределы цикла и рекурсии\n" +
                "3. Используйте кэширование (Соответствие) на верхнем уровне вызова\n\n" +
                "Пример исправления:\n" +
                "   // Вместо рекурсивного обхода с поиском\n" +
                "   // Сделайте один запрос всех данных до начала обхода\n" +
                "   ВсеДанные = Запрос.Выполнить().Выгрузить();\n" +
                "   Соответствие = Новый Соответствие;\n" +
                "   Для каждого Строка Из ВсеДанные Цикл\n" +
                "       Соответствие.Вставить(Строка.Код, Строка.Ссылка);\n" +
                "   КонецЦикла;\n" +
                "   // Затем итеративный обход (стеком) без рекурсии и без запросов";
    }

    /**
     * Рекомендация для глубоких обращений с учётом рекурсии
     */
    private static String getDotAccessRecommendationWithRecursion(String fullChain, int dotCount) {
        return "🔴🔴 КРИТИЧЕСКАЯ ПРОБЛЕМА: РЕКУРСИЯ + ГЛУБОКИЙ ДОСТУП В ЦИКЛЕ 🔴🔴\n\n" +
                "Каждый уровень рекурсии умножает количество обращений к БД.\n\n" +
                "Решение:\n" +
                "1. Получить все данные одним запросом ДО начала рекурсии/цикла\n" +
                "2. Передавать уже полученные данные параметром в рекурсивную функцию\n" +
                "3. Использовать итеративный алгоритм вместо рекурсии\n\n" +
                "Пример:\n" +
                "   // Загружаем все данные один раз\n" +
                "   Запрос = Новый Запрос(\"ВЫБРАТЬ ...\");\n" +
                "   КэшДанных = Запрос.Выполнить().Выгрузить();\n" +
                "   // Передаём кэш в итеративный обход (стек)\n" +
                "   ОбходСтеком(НачальныйЭлемент, КэшДанных);";
    }



    private static int findPositionInOriginal(String originalLine, String chain) {
        String normalizedChain = chain.replaceAll("\\s+", "");
        String normalizedLine = originalLine.replaceAll("\\s+", "");

        int pos = normalizedLine.indexOf(normalizedChain);
        if (pos == -1) return -1;

        int originalPos = 0;
        int normalizedPos = 0;
        for (int i = 0; i < originalLine.length() && normalizedPos < pos + normalizedChain.length(); i++) {
            if (!Character.isWhitespace(originalLine.charAt(i))) {
                normalizedPos++;
            }
            originalPos = i;
            if (normalizedPos > pos) break;
        }
        return originalPos;
    }

    private static int getGlobalPosition(String body, int lineIdx, int posInLine) {
        int globalPos = 0;
        String[] lines = body.split("\n");
        for (int i = 0; i < lineIdx; i++) {
            globalPos += lines[i].length() + 1;
        }
        return globalPos + posInLine;
    }

    private static List<int[]> collectLoopHeaderRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        for (Pattern p : List.of(
                PATTERN_LOOP_HEADER_FOREACH,
                PATTERN_LOOP_HEADER_NUMERIC,
                PATTERN_LOOP_HEADER_WHILE
        )) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                ranges.add(new int[]{m.start(), m.end()});
            }
        }
        return ranges;
    }

    private static boolean isInsideHeaderRange(int position, List<int[]> ranges) {
        for (int[] range : ranges) {
            if (position >= range[0] && position <= range[1]) return true;
        }
        return false;
    }

    private static int getLineNumberInBody(String body, int matchPosition, int cycleStartLine) {
        if (matchPosition <= 0) return cycleStartLine;
        String beforeMatch = body.substring(0, matchPosition);
        int linesBefore = beforeMatch.split("\n", -1).length;
        return cycleStartLine + linesBefore;
    }

    private static String extractSnippet(String text, int matchPosition, int length) {
        int start = Math.max(0, matchPosition - 30);
        int end = Math.min(text.length(), matchPosition + length);
        return text.substring(start, end).replace("\n", " ").trim();
    }

    private static String extractSnippetFromLine(String line, int pos, int length) {
        int start = Math.max(0, pos - 30);
        int end = Math.min(line.length(), pos + length);
        return line.substring(start, end).trim();
    }

    private static String getFindRecommendation(String findType) {
        return "🔧 Вынесите поиск элементов за пределы цикла.\n" +
                "   Вместо:\n" +
                "       Для каждого Строка Из Таблица Цикл\n" +
                "           Элемент = Справочники.Номенклатура." + findType + "(Строка.Код);\n" +
                "       КонецЦикла;\n" +
                "   Сделайте:\n" +
                "       // Один запрос до цикла — получаем всё сразу\n" +
                "       Соответствие = Новый Соответствие;\n" +
                "       Запрос = Новый Запрос(\"ВЫБРАТЬ Ссылка, Код ИЗ Справочник.Номенклатура\");\n" +
                "       Результат = Запрос.Выполнить().Выгрузить();\n" +
                "       Для каждого Стр Из Результат Цикл\n" +
                "           Соответствие.Вставить(Стр.Код, Стр.Ссылка);\n" +
                "       КонецЦикла;\n" +
                "       // Теперь цикл не трогает БД\n" +
                "       Для каждого Строка Из Таблица Цикл\n" +
                "           Элемент = Соответствие.Получить(Строка.Код);\n" +
                "       КонецЦикла;";
    }

    private static String getDotAccessRecommendation(String fullChain, int dotCount) {
        if (dotCount == 2) {
            return "🔧 Рекомендуется вынести получение данных за пределы цикла.\n" +
                    "   Вместо:\n" +
                    "       Для каждого Строка Из Таблица Цикл\n" +
                    "           Значение = " + fullChain + ";\n" +
                    "       КонецЦикла;\n" +
                    "   Сделайте:\n" +
                    "       // Получите все данные одним запросом до цикла\n" +
                    "       Запрос = Новый Запрос(\"ВЫБРАТЬ ...\");\n" +
                    "       // Или вынесите повторяющееся обращение за цикл";
        } else {
            return "🔧 КРИТИЧЕСКОЕ глубокое обращение к реквизитам (" + dotCount + " уровня).\n" +
                    "   Рекомендуется полностью переработать подход:\n" +
                    "   1. Получить все необходимые данные одним запросом до цикла\n" +
                    "   2. Использовать соответствие для быстрого доступа\n" +
                    "   3. Избегать многократного обращения к реквизитам вложенных объектов\n\n" +
                    "   Пример:\n" +
                    "       // Вместо цикла с глубокими обращениями:\n" +
                    "       Для каждого Строка Из Таблица Цикл\n" +
                    "           Сумма = " + fullChain + ";\n" +
                    "       КонецЦикла;\n\n" +
                    "       // Сделайте запрос, который сразу вернет все нужные данные:\n" +
                    "       Запрос = Новый Запрос(\"ВЫБРАТЬ ...\");\n" +
                    "       Результат = Запрос.Выполнить().Выгрузить();\n" +
                    "       // ... и работайте с результатом запроса";
        }
    }
}