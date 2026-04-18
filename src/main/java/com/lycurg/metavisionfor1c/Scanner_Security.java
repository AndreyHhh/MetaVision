package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class Scanner_Security {

    // =========================================================================
    // QUICK_FILTER — lowercase, без (?i)
    // =========================================================================
    private static final Pattern QUICK_FILTER = Pattern.compile(
            "(выполнить|вычислить|" +
                    "comобъект|comobject|" +
                    "изменить\\s|удалить\\s+из|вставить\\s+в|" +
                    "(пароль|токен|secret|password)\\s*=\\s*[\"']|" +
                    "AKIA[0-9A-Z]{16}|sk-[a-zA-Z0-9]{32,})"
    );

    // =========================================================================
    // CRITICAL — Code Execution
    // =========================================================================
    private static final Pattern PATTERN_CODE_EXECUTION = Pattern.compile(
            "(?m)^(?!\\s*//).*(?:^|[^\\w.])(выполнить|вычислить)\\s*\\([^)\\n]*\\+[^)\\n]*\\)"
    );

    private static final Pattern PATTERN_CODE_EXECUTION_FP = Pattern.compile(
            "(проверитьимяпроцедурыконфигурации|" +
                    "апк:488|" +
                    "вызватьфункциюконфигурации|" +
                    "(выполнить|вычислить)\\s*\\([^)]+\\+\\s*\"\\([^\"]*\\)\"\\s*\\))"
    );

    private static final Pattern PATTERN_EXTERNAL_INPUT = Pattern.compile(
            "(этотобъект\\.|объект\\.|элементы\\.|данныеформы\\." +
                    "|реквизитформывзначение|параметрысеанса\\." +
                    "|запрос\\.установитьпараметр|получитьизвременногохранилища" +
                    "|вводстроки|вводчисла|вводдаты)"
    );

    // =========================================================================
    // CRITICAL — Unsafe COM
    // =========================================================================
    private static final Pattern PATTERN_UNSAFE_COM = Pattern.compile(
            "(?i)(?:новый\\s+)?com[оo]бъект\\s*\\([^)\\n]+" +
                    "(filesystemobject|wscript\\.shell|scripting\\.filesystemobject|" +
                    "shell\\.application|wbemscripting\\.swbemlocator|microsoft\\.xmlhttp)"
    );

    // =========================================================================
    // CRITICAL — DML Injection
    // =========================================================================
    private static final Pattern PATTERN_DML_INJECTION = Pattern.compile(
            "(?si)\"[^\"]*\\b(ИЗМЕНИТЬ|УДАЛИТЬ\\s+ИЗ|ВСТАВИТЬ\\s+В)\\b.{0,600}\"\\s*\\+\\s*[а-яёa-z_]|" +
                    "запрос\\.текст\\s*[+]=.{0,300}\\b(изменить|удалить|вставить)\\b"
    );
    // =========================================================================
    // HIGH — Hardcoded Secret
    // =========================================================================
    private static final Pattern PATTERN_HARDCODED_CREDS = Pattern.compile(
            "(?m)^(?!\\s*//).*\\b(пароль|токен|secret|password|accesstoken)" +
                    "\\s*=\\s*[\"'][^\\s\"']{6,}[\"']|" +
                    "AKIA[0-9A-Z]{16}|" +
                    "sk-[a-zA-Z0-9]{40,}"
    );

    private static final Pattern PATTERN_HARDCODED_CREDS_FP = Pattern.compile(
            "(подсказка|заголовок|метка|текст|описание|кнопка|вопрос|сообщение|ошибка)" +
                    ".{0,30}(пароль|токен)" +
                    "|[\"']\\*+[\"']" +
                    "|[\"']-+[\"']"
    );



    // =========================================================================
    // Публичный API
    // =========================================================================
    public static class SecurityIssue {
        public int functionId;      // 🔥 для точного поиска по id
        public String type;
        public String severity;
        public String description;
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public String filePath;
        public String vulnerableCode;
        public int lineNumber;
        public String recommendation;

        public SecurityIssue(int functionId, String type, String severity, String description,
                             String functionName, String objectFullName, String moduleType,
                             String filePath, String vulnerableCode, int lineNumber,
                             String recommendation) {
            this.functionId = functionId;
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.functionName = functionName;
            this.objectFullName = objectFullName;
            this.moduleType = moduleType;
            this.filePath = filePath;
            this.vulnerableCode = vulnerableCode;
            this.lineNumber = lineNumber;
            this.recommendation = recommendation;
        }
    }

    // =========================================================================
    // Внутренняя структура
    // =========================================================================
    private static class FunctionData {
        final int id;
        final String functionName, functionText, functionTextFind;
        final String objectFullName, moduleType, filePath;
        final int startLine;

        FunctionData(int id, String functionName, String functionText, String functionTextFind,
                     int startLine, String objectFullName, String moduleType, String filePath) {
            this.id = id;
            this.functionName = functionName;
            this.functionText = functionText;
            this.functionTextFind = functionTextFind;
            this.startLine = startLine;
            this.objectFullName = objectFullName;
            this.moduleType = moduleType;
            this.filePath = filePath;
        }
    }

    // =========================================================================
    // Сканирование
    // =========================================================================
    public static List<SecurityIssue> scanForSecurityIssues() {
        List<SecurityIssue> issues = new CopyOnWriteArrayList<>();

        String sql = "SELECT mf.id, mf.function_name, mf.function_text, mf.function_text_find, mf.start_line, " +
                "mm.object_full_name, mm.module_type, mm.file_path " +
                "FROM metadata_functions mf " +
                "JOIN metadata_modules mm ON mf.module_id = mm.id " +
                "WHERE mf.function_text_find IS NOT NULL " +
                "AND LENGTH(mf.function_text_find) > 50";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA cache_size = -1024000");
                stmt.execute("PRAGMA temp_store = MEMORY");
                stmt.execute("PRAGMA mmap_size = 536870912");
                stmt.execute("PRAGMA read_uncommitted = 1");
            }

            long startTime = System.currentTimeMillis();
            int totalFunctions = 0;

            int numThreads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<?>> futures = new ArrayList<>();

            System.out.println("🔒 Сканер безопасности запущен (" + numThreads + " потоков)...");

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                List<FunctionData> batch = new ArrayList<>(500);

                while (rs.next()) {
                    String textFind = rs.getString("function_text_find");
                    totalFunctions++;

                    // 🔥 QUICK_FILTER по function_text_find
                    if (!QUICK_FILTER.matcher(textFind).find()) continue;

                    batch.add(new FunctionData(
                            rs.getInt("id"),
                            rs.getString("function_name"),
                            rs.getString("function_text"),
                            textFind,
                            rs.getInt("start_line"),
                            rs.getString("object_full_name"),
                            rs.getString("module_type"),
                            rs.getString("file_path")
                    ));

                    if (batch.size() >= 500) {
                        List<FunctionData> copy = new ArrayList<>(batch);
                        futures.add(executor.submit(() -> processBatch(copy, issues)));
                        batch.clear();

                        if (totalFunctions % 10000 == 0) {
                            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                            System.out.printf("🔒 %d функций (%.0f/сек) | найдено: %d%n",
                                    totalFunctions,
                                    totalFunctions / Math.max(1.0, elapsed),
                                    issues.size());
                        }
                    }
                }

                if (!batch.isEmpty()) {
                    futures.add(executor.submit(() -> processBatch(batch, issues)));
                }
                for (Future<?> f : futures) f.get();
            }

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            System.out.printf("✅ Завершено: %d функций за %d сек | найдено: %d уязвимостей%n",
                    totalFunctions, elapsed, issues.size());

        } catch (Exception e) {
            System.err.println("❌ Ошибка сканирования: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    private static void processBatch(List<FunctionData> batch, List<SecurityIssue> issues) {
        for (FunctionData fd : batch) scanFunction(fd, issues);
    }

    private static void scanFunction(FunctionData fd, List<SecurityIssue> issues) {
        String clean = stripLineComments(fd.functionTextFind);
        boolean isServer = clean.contains("&насервере") ||
                clean.contains("&насерверебезконтекста");

        // --- CRITICAL: Code Execution ---
        if (PATTERN_CODE_EXECUTION.matcher(clean).find()) {
            if (!PATTERN_CODE_EXECUTION_FP.matcher(clean).find()) {
                boolean hasExternalInput = PATTERN_EXTERNAL_INPUT.matcher(clean).find();
                if (hasExternalInput) {
                    findAll(PATTERN_CODE_EXECUTION, clean, fd, issues,
                            "Code Execution", "CRITICAL",
                            "Выполнить()/Вычислить() с конкатенацией — аргумент может содержать внешний ввод",
                            getRecommendation("CODE_EXECUTION"));
                } else {
                    findAll(PATTERN_CODE_EXECUTION, clean, fd, issues,
                            "Code Execution", "HIGH",
                            "Выполнить()/Вычислить() с конкатенацией — убедитесь что аргумент не из внешнего ввода",
                            getRecommendation("CODE_EXECUTION"));
                }
            }
        }

        // --- CRITICAL: Unsafe COM ---
        if (isServer && PATTERN_UNSAFE_COM.matcher(clean).find()) {
            findAll(PATTERN_UNSAFE_COM, clean, fd, issues,
                    "Unsafe COM Object", "CRITICAL",
                    "Опасный COM-объект в серверном коде (&НаСервере)",
                    getRecommendation("UNSAFE_COM"));
        }

        // --- CRITICAL: DML Injection ---
        if (PATTERN_DML_INJECTION.matcher(clean).find()) {
            findAll(PATTERN_DML_INJECTION, clean, fd, issues,
                    "DML Injection", "CRITICAL",
                    "Конкатенация переменной в запросе на изменение/удаление данных",
                    getRecommendation("DML_INJECTION"));
        }



        // --- HIGH: Hardcoded Secret ---
        Matcher credsMatcher = PATTERN_HARDCODED_CREDS.matcher(clean);
        while (credsMatcher.find()) {
            String ctx = getContext(clean, credsMatcher.start(), 80);
            if (PATTERN_HARDCODED_CREDS_FP.matcher(ctx).find()) continue;
            issues.add(makeIssue("Hardcoded Secret", "HIGH",
                    "Пароль, токен или ключ зашит строковым литералом в коде",
                    getRecommendation("HARDCODED_SECRET"),
                    fd, clean, credsMatcher.start()));
        }
    }

    // =========================================================================
    // Утилиты
    // =========================================================================
    private static String stripLineComments(String text) {
        return text.replaceAll("(?m)//[^\n]*", "");
    }

    private static void findAll(Pattern pattern, String text, FunctionData fd,
                                List<SecurityIssue> issues, String type, String severity,
                                String description, String recommendation) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            issues.add(makeIssue(type, severity, description, recommendation, fd, text, m.start()));
        }
    }

    private static void findFirst(Pattern pattern, String text, FunctionData fd,
                                  List<SecurityIssue> issues, String type, String severity,
                                  String description, String recommendation) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            issues.add(makeIssue(type, severity, description, recommendation, fd, text, m.start()));
        }
    }

    private static SecurityIssue makeIssue(String type, String severity, String description,
                                           String recommendation, FunctionData fd,
                                           String text, int pos) {
        return new SecurityIssue(
                fd.id,          // 🔥 id для точного поиска
                type, severity, description,
                fd.functionName, fd.objectFullName, fd.moduleType, fd.filePath,
                getLine(text, pos),
                countNewlines(text.substring(0, pos)) + 1,
                recommendation);
    }

    private static String getLine(String text, int pos) {
        String[] lines = text.split("\n", -1);
        int idx = countNewlines(text.substring(0, pos));
        if (idx < lines.length) {
            String line = lines[idx].trim();
            return line.length() > 120 ? line.substring(0, 117) + "..." : line;
        }
        return "";
    }

    private static String getContext(String text, int pos, int radius) {
        return text.substring(Math.max(0, pos - radius), Math.min(text.length(), pos + radius));
    }

    private static int countNewlines(String str) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') count++;
        }
        return count;
    }

    // =========================================================================
    // Рекомендации
    // =========================================================================
    private static String getRecommendation(String type) {
        switch (type) {
            case "CODE_EXECUTION":
                return "Выполнить()/Вычислить() собирает строку через конкатенацию (+). " +
                        "Если в аргумент попадёт внешний ввод — выполнится произвольный код 1С. " +
                        "Проверьте что источник строки — только константы или внутренние переменные.";
            case "UNSAFE_COM":
                return "Опасный COM-объект запущен в серверном контексте. " +
                        "Замените на встроенные функции 1С: КопироватьФайл(), ЗапуститьПриложение(). " +
                        "Если без COM нельзя — вынесите в изолированный внешний сервис.";
            case "DML_INJECTION":
                return "В запросах ИЗМЕНИТЬ/УДАЛИТЬ/ВСТАВИТЬ используйте параметры вместо конкатенации:\n" +
                        "Запрос.Текст = \"ИЗМЕНИТЬ ... ГДЕ Поле = &Параметр\";\n" +
                        "Запрос.УстановитьПараметр(\"Параметр\", Значение);";

            case "HARDCODED_SECRET":
                return "Пароль или токен зашит в коде — попадёт в выгрузку конфигурации и git.\n" +
                        "Храните секреты в:\n" +
                        "• Константах с ограниченным доступом по ролям\n" +
                        "• Параметрах сеанса\n" +
                        "• Безопасном хранилище: ОбщегоНазначения.ПрочитатьДанныеИзБезопасногоХранилища()";
            default:
                return "Требуется ручная проверка.";
        }
    }

    // =========================================================================
    // Статистика
    // =========================================================================
    public static Map<String, Integer> getSecurityStatistics(List<SecurityIssue> issues) {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("TOTAL", issues.size());
        stats.put("CRITICAL", 0);
        stats.put("HIGH", 0);
        for (SecurityIssue issue : issues) {
            stats.merge(issue.severity, 1, Integer::sum);
        }
        return stats;
    }

    public static Map<String, List<SecurityIssue>> groupByType(List<SecurityIssue> issues) {
        Map<String, List<SecurityIssue>> grouped = new LinkedHashMap<>();
        for (SecurityIssue issue : issues) {
            grouped.computeIfAbsent(issue.type, k -> new ArrayList<>()).add(issue);
        }
        return grouped;
    }
}