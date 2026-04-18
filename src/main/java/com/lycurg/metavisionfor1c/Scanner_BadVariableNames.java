package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class Scanner_BadVariableNames {

    // ========== ВНУТРЕННИЙ КЛАСС ДЛЯ ПЛОХИХ ИМЕН ==========
    public static class BadVariableIssue {
        public String type;
        public String severity;       // CRITICAL / HIGH / MEDIUM / LOW
        public String variableName;
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public String filePath;
        public int lineNumber;
        public String context;
        public String recommendation;

        public BadVariableIssue(String type, String severity, String variableName,
                                String functionName, String objectFullName, String moduleType,
                                String filePath, int lineNumber, String context,
                                String recommendation) {
            this.type = type;
            this.severity = severity;
            this.variableName = variableName;
            this.functionName = functionName;
            this.objectFullName = objectFullName;
            this.moduleType = moduleType;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.context = context;
            this.recommendation = recommendation;
        }
    }

    // ========== КЛАСС ДЛЯ НЕИСПОЛЬЗУЕМЫХ ФУНКЦИЙ ==========
    public static class UnusedFunctionIssue {
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public String filePath;
        public int lineNumber;
        public String recommendation;

        public UnusedFunctionIssue(String functionName, String objectFullName,
                                   String moduleType, String filePath, int lineNumber,
                                   String recommendation) {
            this.functionName = functionName;
            this.objectFullName = objectFullName;
            this.moduleType = moduleType;
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.recommendation = recommendation;
        }
    }

    // FIXED for Windows - все прямые русские буквы заменены на Unicode диапазоны
    private static final List<Pattern> BAD_NAME_PATTERNS = Arrays.asList(
            Pattern.compile("^(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)$", Pattern.CASE_INSENSITIVE),
            // Русские одиночные буквы (а-я, ё) - заменены на Unicode
            Pattern.compile("^(\\u0430|\\u0431|\\u0432|\\u0433|\\u0434|\\u0435|\\u0451|\\u0436|\\u0437|\\u0438|\\u0439|\\u043a|\\u043b|\\u043c|\\u043d|\\u043e|\\u043f|\\u0440|\\u0441|\\u0442|\\u0443|\\u0444|\\u0445|\\u0446|\\u0447|\\u0448|\\u0449|\\u044a|\\u044b|\\u044c|\\u044d|\\u044e|\\u044f)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(ab|ac|ad|ba|bb|bc|ca|cb|cc|aa|bb|cc|dd|ee|ff|gg|hh)$", Pattern.CASE_INSENSITIVE),
            // Русские двухбуквенные комбинации - заменены на Unicode
            Pattern.compile("^(\\u0430\\u0431|\\u0430\\u0432|\\u0430\\u0433|\\u0431\\u0430|\\u0431\\u0431|\\u0431\\u0432|\\u0432\\u0430|\\u0432\\u0431|\\u0432\\u0432|\\u0430\\u0430|\\u0431\\u0431|\\u0432\\u0432|\\u0433\\u0433|\\u0434\\u0434|\\u0435\\u0435)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(tmp|temp|temporary)(\\d*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(var|val|dat)(\\d*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(perem|peremen|перем)(\\d*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(сч|счет|сч_|счет_|сч\\d+|счет\\d+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(п|пп|ппп|пр|пром|промеж)(\\d*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(р|рез_|рез\\d+|res_)(\\d*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(д|дн|дан|data)(\\d*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(сп|спс|list|тз|тзн|tab)(\\d*)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(строк|string)(\\d*)$", Pattern.CASE_INSENSITIVE),
            // FIXED: [a-zа-я] заменён на [a-z\\u0430-\\u044f]
            Pattern.compile("^[a-z\\u0430-\\u044f]\\d+$", Pattern.CASE_INSENSITIVE),
            // FIXED: [a-zа-я]{1,2} заменён на [a-z\\u0430-\\u044f]{1,2}
            Pattern.compile("^[a-z\\u0430-\\u044f]{1,2}$", Pattern.CASE_INSENSITIVE)
    );

    // Исключения (нормальные имена)
    private static final Set<String> EXCEPTIONS = new HashSet<>(Arrays.asList(
            "i", "j", "k", "n", "m", "id", "ID", "Id",
            "Стр", "Строка", "СтрокаТаблицы", "Элемент", "ТекСтрока", "ТекЭлемент",
            "Поле", "Колонка", "СтрокаТаблицыЗначений", "ТекТаблица",
            "НомерСтроки", "Индекс", "Дата", "ДатаНач", "ДатаКон",
            "Сумма", "Количество", "Цена", "Имя", "Код", "Наименование",
            "Список", "Массив", "Структура", "Соответствие", "Таблица",
            "Row", "Item", "CurrentRow", "CurrentItem", "Index", "Count", "Total", "Amount",
            "Name", "Code", "Description",
            "data", "Data", "value", "Value", "str", "Str", "сч", "IP", "ip", "pass",
            // 🔥 нормальные сокращения
            "Result", "result", "RESULT",
            "тз", "ТЗ", "Тз",
            "стр", "Стр", "СТР",
            "res", "Res", "RES"
    ));

    // ========== МЕТОДЫ ДЛЯ ПОИСКА ПЛОХИХ ИМЕН ==========
    public static List<BadVariableIssue> scanForBadVariableNames() {
        List<BadVariableIssue> issues = new CopyOnWriteArrayList<>();

        String sql = """
            SELECT 
                mf.id AS function_id,
                mf.function_name,
                mf.function_text,
                mf.start_line,
                mm.object_full_name,
                mm.module_type,
                mm.file_path
            FROM metadata_functions mf
            JOIN metadata_modules mm ON mf.module_id = mm.id
            WHERE mf.function_text IS NOT NULL
            ORDER BY mm.object_full_name, mf.function_name
            """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String functionName = rs.getString("function_name");
                String functionText = rs.getString("function_text");
                String objectFullName = rs.getString("object_full_name");
                String moduleType = rs.getString("module_type");
                String filePath = rs.getString("file_path");
                int startLine = rs.getInt("start_line");

                analyzeFunctionForBadNames(
                        functionName, functionText, objectFullName,
                        moduleType, filePath, startLine, issues
                );
            }

            System.out.println("📊 Найдено проблем с именами переменных: " + issues.size());

        } catch (SQLException e) {
            System.err.println("❌ Ошибка анализа имен переменных: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    private static void analyzeFunctionForBadNames(
            String functionName, String functionText, String objectFullName,
            String moduleType, String filePath, int startLine,
            List<BadVariableIssue> issues) {

        Set<String> foundBadNames = new HashSet<>();
        String[] lines = functionText.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int currentLine = i + 1;


            // 🔥 Пропускаем комментарии
            String trimmed = line.trim();
            if (trimmed.startsWith("//")) continue;

            if (trimmed.startsWith("|") || trimmed.startsWith("||") ) continue;

            // Обрезаем инлайн-комментарий
            int commentIdx = line.indexOf("//");
            if (commentIdx > 0) {
                // Проверяем что // не внутри строки
                if (!isInsideQuotesOrPipe(line, commentIdx)) {
                    line = line.substring(0, commentIdx);
                }
            }



            // 1. Объявления переменных Перем
            Matcher varMatcher = Pattern.compile(
                    "Перем\\s+([^;]+);",
                    Pattern.CASE_INSENSITIVE
            ).matcher(line);

            if (varMatcher.find()) {
                if (!isInsideQuotesOrPipe(line, varMatcher.start())) {
                    String varDecl = varMatcher.group(1);
                    String[] vars = varDecl.split(",");
                    for (String var : vars) {
                        String varName = var.trim().split("=")[0].trim();
                        if (isBadVariableName(varName) && !foundBadNames.contains(varName)) {
                            foundBadNames.add(varName);
                            issues.add(new BadVariableIssue(
                                    "Объявление переменной",
                                    determineSeverity(varName),
                                    varName,
                                    functionName,
                                    objectFullName,
                                    moduleType,
                                    filePath,
                                    currentLine,
                                    "Перем " + varName,
                                    generateRecommendation(varName)
                            ));
                        }
                    }
                }
            }

            // 2. Параметры функций
            Matcher paramMatcher = Pattern.compile(
                    "(?:Функция|Процедура)\\s+\\w+\\s*\\(([^)]*)\\)",
                    Pattern.CASE_INSENSITIVE
            ).matcher(line);

            if (paramMatcher.find()) {
                if (!isInsideQuotesOrPipe(line, paramMatcher.start())) {
                    String params = paramMatcher.group(1);
                    if (!params.trim().isEmpty()) {
                        String[] paramList = params.split(",");
                        for (String param : paramList) {
                            String paramName = param.trim().split("\\s+")[0].trim();
                            paramName = paramName.split("=")[0].trim();
                            if (isBadVariableName(paramName) && !foundBadNames.contains(paramName)) {
                                foundBadNames.add(paramName);
                                issues.add(new BadVariableIssue(
                                        "Параметр функции",
                                        determineSeverity(paramName),
                                        paramName,
                                        functionName,
                                        objectFullName,
                                        moduleType,
                                        filePath,
                                        currentLine,
                                        "Параметр: " + paramName,
                                        generateRecommendation(paramName)
                                ));
                            }
                        }
                    }
                }
            }

            // 3. Присваивания
            // FIXED for Windows: [a-zа-яё] заменён на [a-z\\u0430-\\u044f\\u0451]
            Matcher assignMatcher = Pattern.compile(
                    "\\b([a-z\\u0430-\\u044f\\u0451][a-z\\u0430-\\u044f\\u04510-9_]*)\\s*=",
                    Pattern.CASE_INSENSITIVE
            ).matcher(line);

            while (assignMatcher.find()) {
                if (isInsideQuotesOrPipe(line, assignMatcher.start())) {
                    continue;
                }

                String varName = assignMatcher.group(1);
                if (isKeyword(varName)) continue;
                if (foundBadNames.contains(varName)) continue;
                if (isBadVariableName(varName)) {
                    if (isLoopCounter(varName, line)) continue;
                    foundBadNames.add(varName);
                    issues.add(new BadVariableIssue(
                            "Присваивание переменной",
                            determineSeverity(varName),
                            varName,
                            functionName,
                            objectFullName,
                            moduleType,
                            filePath,
                            currentLine,
                            varName + " = ...",
                            generateRecommendation(varName)
                    ));
                }
            }
        }
    }

    private static boolean isInsideQuotesOrPipe(String line, int position) {
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;

        for (int i = 0; i < position && i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"' && (i == 0 || line.charAt(i-1) != '\\')) {
                inDoubleQuotes = !inDoubleQuotes;
            }
            else if (c == '\'' && (i == 0 || line.charAt(i-1) != '\\')) {
                inSingleQuotes = !inSingleQuotes;
            }
            else if (c == '|' && !inDoubleQuotes && !inSingleQuotes) {
                return true;
            }
        }

        return inDoubleQuotes || inSingleQuotes;
    }

    private static boolean isBadVariableName(String name) {
        if (name == null || name.isEmpty()) return false;
        if (EXCEPTIONS.contains(name)) return false;
        for (Pattern pattern : BAD_NAME_PATTERNS) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKeyword(String word) {
        Set<String> keywords = new HashSet<>(Arrays.asList(
                "Если", "Тогда", "Иначе", "КонецЕсли", "Для", "Пока", "Цикл", "КонецЦикла",
                "Попытка", "Исключение", "КонецПопытки", "Перем", "Процедура", "Функция",
                "КонецПроцедуры", "КонецФункции", "Возврат", "Продолжить", "Прервать",
                "Новый", "Значение", "Неопределено", "Истина", "Ложь"
        ));
        return keywords.contains(word);
    }

    // FIXED for Windows - метод matches с русскими буквами заменён на Unicode
    private static boolean isLoopCounter(String varName, String line) {
        if (varName.matches("(?i)^[ijk]$")) {
            String lowerLine = line.toLowerCase();
            return lowerLine.contains("цикл") ||
                    lowerLine.contains("для") ||
                    lowerLine.contains("пока");
        }
        return false;
    }

    private static String determineSeverity(String varName) {
        // FIXED for Windows - [а-я] заменён на [\\u0430-\\u044f]
        if (varName.length() <= 2) return "HIGH";
        if (varName.matches("(?i)^(tmp|temp|var|val|data|res|perem|перем|сч|счет|п|пп|р|рез|д|дн|сп|спс|стр).*"))
            return "MEDIUM";
        return "LOW";
    }

    private static String generateRecommendation(String varName) {
        return "⚠️ Плохое имя переменной: \"" + varName + "\"\n\n" +
                "Используйте осмысленные имена, отражающие суть данных.\n" +
                "Пример: \"СуммаЗаказа\", \"КоличествоТоваров\", \"ДатаНачала\"";
    }

    // ========== МЕТОД ДЛЯ СТАТИСТИКИ (КАК В Scanner_BackgroundJobs) ==========
    public static Map<String, Object> getStatistics(List<BadVariableIssue> issues) {
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("TOTAL", issues.size());

        // Подсчет по severity
        Map<String, Integer> bySeverity = new LinkedHashMap<>();
        bySeverity.put("CRITICAL", 0);
        bySeverity.put("HIGH", 0);
        bySeverity.put("MEDIUM", 0);
        bySeverity.put("LOW", 0);

        for (BadVariableIssue issue : issues) {
            bySeverity.merge(issue.severity, 1, Integer::sum);
        }

        stats.put("BY_SEVERITY", bySeverity);

        // Дополнительная статистика по типам проблем
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (BadVariableIssue issue : issues) {
            byType.merge(issue.type, 1, Integer::sum);
        }
        stats.put("BY_TYPE", byType);

        return stats;
    }
}