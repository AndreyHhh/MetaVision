package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;


//# Сканер производительности: поиск запросов в циклах, анализ цепочек вызовов и определение глубины вложенности
public class Scanner_Performance {

    // ========== КОНСТАНТЫ ==========
    private static final int MAX_CALL_CHAIN_DEPTH = 100;        // Макс глубина цепочки вызовов
    private static final int MIN_CYCLES_FOR_ISSUE = 2;          // Минимум циклов для показа проблемы



    public static class PerformanceIssue {
        public String type;
        public String severity;
        public String description;
        public String functionName;
        public String objectFullName;
        public String moduleType;
        public String filePath;
        public String problematicCode;
        public int lineNumber;
        public String recommendation;
        public String chainPath;
        public int chainDepth;
        public int functionId;
        public String chainInfo;           // ← ДОБАВИТЬ НОВОЕ ПОЛЕ
        public List<Integer> chainFunctionIds;

        // Существующий конструктор (оставь как есть для обратной совместимости)
        public PerformanceIssue(String type, String severity, String description,
                                String functionName, String objectFullName, String moduleType,
                                String filePath, String problematicCode, int lineNumber,
                                String recommendation, String chainPath, int chainDepth,
                                int functionId) {
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.functionName = functionName;
            this.objectFullName = objectFullName;
            this.moduleType = moduleType;
            this.filePath = filePath;
            this.problematicCode = problematicCode;
            this.lineNumber = lineNumber;
            this.recommendation = recommendation;
            this.chainPath = chainPath;
            this.chainDepth = chainDepth;
            this.functionId = functionId;
            this.chainInfo = chainDepth + " уровней"; // значение по умолчанию
        }

        // НОВЫЙ КОНСТРУКТОР с chainInfo
        public PerformanceIssue(String type, String severity, String description,
                                String functionName, String objectFullName, String moduleType,
                                String filePath, String problematicCode, int lineNumber,
                                String recommendation, String chainPath, int chainDepth,
                                int functionId, String chainInfo) {
            this(type, severity, description, functionName, objectFullName, moduleType,
                    filePath, problematicCode, lineNumber, recommendation, chainPath, chainDepth, functionId);
            this.chainInfo = chainInfo;
        }
    }

    private static class CallInLoop {
        int callerFunctionId;
        String callerFunctionName;
        int cyclesCount;

        CallInLoop(int callerFunctionId, String callerFunctionName, int cyclesCount) {
            this.callerFunctionId = callerFunctionId;
            this.callerFunctionName = callerFunctionName;
            this.cyclesCount = cyclesCount;
        }
    }

    private static class QueryElement {
        int elementId;
        int functionId;
        String functionName;
        String elementType;
        String elementName;
        int startLine;

        QueryElement(int elementId, int functionId, String functionName,
                     String elementType, String elementName, int startLine) {
            this.elementId = elementId;
            this.functionId = functionId;
            this.functionName = functionName;
            this.elementType = elementType;
            this.elementName = elementName;
            this.startLine = startLine;
        }
    }

    // ========== ОСНОВНОЙ МЕТОД ==========
    public static List<PerformanceIssue> scanForPerformanceIssues() {
        List<PerformanceIssue> issues = new ArrayList<>();

        try (Connection conn = createConnection()) {
            System.out.println("⚡ Сканирование производительности...");

            List<QueryElement> queryElements = findQueryElements(conn);
            System.out.println("🔍 Найдено запросов: " + queryElements.size());

            for (QueryElement query : queryElements) {
                try {
                    CallChainResult chainResult = analyzeCallChain(query, conn);

                    if (chainResult.totalCycles >= MIN_CYCLES_FOR_ISSUE) {
                        PerformanceIssue issue = createIssue(query, chainResult, conn);
                        if (issue != null) {
                            issues.add(issue);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ Ошибка анализа запроса " + query.functionName + ": " + e.getMessage());
                }
            }

            System.out.println("✅ Найдено проблем: " + issues.size());

        } catch (Exception e) {
            System.err.println("❌ Ошибка сканирования: " + e.getMessage());
            e.printStackTrace();
        }

        return issues;
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    private static Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA cache_size = -1024000");
            stmt.execute("PRAGMA temp_store = MEMORY");
        }
        return conn;
    }

    private static List<QueryElement> findQueryElements(Connection conn) throws SQLException {
        List<QueryElement> elements = new ArrayList<>();

        String sql = """
            SELECT ce.id, ce.function_id, ce.function_name, 
                   ce.element_type, ce.element_name, ce.start_line
            FROM code_elements ce
            WHERE ce.element_type IN ('Запрос', 'ЦиклЗапроса')
            ORDER BY ce.function_id, ce.start_line
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                elements.add(new QueryElement(
                        rs.getInt("id"),
                        rs.getInt("function_id"),
                        rs.getString("function_name"),
                        rs.getString("element_type"),
                        rs.getString("element_name"),
                        rs.getInt("start_line")
                ));
            }
        }

        return elements;
    }

    // ========== АНАЛИЗ ЦЕПОЧКИ ВЫЗОВОВ ==========
    private static class CallChainResult {
        List<CallInLoop> chain;
        int totalCycles;
        String fullPath;
        List<Integer> chainFunctionIds;
        CallChainResult(List<CallInLoop> chain, int totalCycles, String fullPath) {
            this.chain = chain;
            this.totalCycles = totalCycles;
            this.fullPath = fullPath;
        }
    }

    private static CallChainResult analyzeCallChain(QueryElement query, Connection conn)
            throws SQLException {

        List<CallInLoop> chain = new ArrayList<>();
        Set<Integer> visitedFunctions = new HashSet<>();
        int totalCycles = 0;

        int currentFunctionId = query.functionId;
        String currentFunctionName = query.functionName;
        int currentCallElementId = query.elementId;  // ← ДОБАВЛЯЕМ ID элемента вызова

        visitedFunctions.add(currentFunctionId);

        for (int depth = 0; depth < MAX_CALL_CHAIN_DEPTH; depth++) {
            // Находим всех, кто вызывает текущую функцию
            List<CallerWithElementInfo> allCallers = findAllCallersWithElementInfo(
                    currentFunctionId,  conn);

            if (allCallers.isEmpty()) {
                break;
            }

            CallerWithElementInfo bestCaller = null;

            for (CallerWithElementInfo caller : allCallers) {
                if (visitedFunctions.contains(caller.functionId)) {
                    continue;
                }

                // 🔥 КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ:
                // Проверяем, находится ли вызов ВНУТРИ цикла в вызывающей функции
                int cyclesOnPath = countCyclesOnCallPath(caller.callElementId, conn);

                if (cyclesOnPath > 0) {
                    // Вызов находится внутри цикла(ов)
                    chain.add(new CallInLoop(caller.functionId, caller.functionName, cyclesOnPath));
                    totalCycles += cyclesOnPath;
                    visitedFunctions.add(caller.functionId);
                    bestCaller = caller;
                    break;
                } else {
                    // Вызов не в цикле, но это промежуточное звено
                    chain.add(new CallInLoop(caller.functionId, caller.functionName, 0));
                    visitedFunctions.add(caller.functionId);
                    currentFunctionId = caller.functionId;
                    currentFunctionName = caller.functionName;
                    bestCaller = caller;
                    break;
                }
            }

            if (bestCaller == null) {
                break;
            }

            currentFunctionId = bestCaller.functionId;
            currentFunctionName = bestCaller.functionName;
        }

        String fullPath = buildReadableChain(query, chain);
       // return new CallChainResult(chain, totalCycles, fullPath);





        // Собираем ID цепочки от корня к проблемной функции
        List<Integer> chainIds = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            chainIds.add(chain.get(i).callerFunctionId);
        }
        chainIds.add(query.functionId); // проблемная функция — последняя

        CallChainResult result = new CallChainResult(chain, totalCycles, fullPath);
        result.chainFunctionIds = chainIds;
        return result;
    }


    // Класс с информацией о вызывающем и ID элемента вызова
    private static class CallerWithElementInfo {
        int functionId;
        String functionName;
        int callElementId;  // ID элемента вызова (тип 'ВызовФункции')

        CallerWithElementInfo(int functionId, String functionName, int callElementId) {
            this.functionId = functionId;
            this.functionName = functionName;
            this.callElementId = callElementId;
        }
    }

    // Находит всех вызывающих с ID элемента вызова
    private static List<CallerWithElementInfo> findAllCallersWithElementInfo(
            int targetFunctionId,  Connection conn) throws SQLException {

        List<CallerWithElementInfo> callers = new ArrayList<>();

        String sql = """
        SELECT ce.function_id, mf.function_name, ce.id as call_element_id
        FROM code_elements ce
        JOIN metadata_functions mf ON ce.function_id = mf.id
        WHERE ce.element_type = 'ВызовФункции'
          AND ce.called_function_id = ?
        ORDER BY mf.function_name
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, targetFunctionId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                callers.add(new CallerWithElementInfo(
                        rs.getInt("function_id"),
                        rs.getString("function_name"),
                        rs.getInt("call_element_id")
                ));
            }
        }

        return callers;
    }

    // Подсчитывает, сколько циклов находится на пути от элемента вызова до корня
    private static int countCyclesOnCallPath(int callElementId, Connection conn) throws SQLException {
        String sql = """
        WITH RECURSIVE path_to_root AS (
            SELECT id, owner_id, element_type, 0 as depth
            FROM code_elements
            WHERE id = ?
            
            UNION ALL
            
            SELECT ce.id, ce.owner_id, ce.element_type, p.depth + 1
            FROM code_elements ce
            JOIN path_to_root p ON ce.id = p.owner_id
            WHERE p.owner_id IS NOT NULL
        )
        SELECT COUNT(*) as cycle_count
        FROM path_to_root
        WHERE element_type IN ('ЦиклНезависимый', 'ЦиклЗапроса')
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, callElementId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("cycle_count");
            }
        }
        return 0;
    }

    // СТРОИТ ПОНЯТНУЮ ЦЕПОЧКУ С ПРАВИЛЬНЫМ ПОРЯДКОМ
    // СТРОИТ ДРЕВОВИДНУЮ ЦЕПОЧКУ (от запроса к корню)
    private static String buildReadableChain(QueryElement query, List<CallInLoop> chain) {
        StringBuilder sb = new StringBuilder();

        sb.append("🔍 ЦЕПОЧКА ВЫЗОВОВ (от запроса к корню):\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // УРОВЕНЬ 0 - САМ ЗАПРОС
        sb.append("[УРОВЕНЬ 0] 📌 ЗАПРОС\n");
        sb.append("            └─ Функция: ").append(query.functionName).append("\n");
        sb.append("                Запрос.Выполнить()\n\n");

        // ПРОХОДИМ ПО ЦЕПОЧКЕ (от ближайшего к дальнему)
        for (int i = 0; i < chain.size(); i++) {
            CallInLoop call = chain.get(i);
            int level = i + 1;

            // Определяем тип вызова
            String callType;
            if (call.cyclesCount > 0) {
                if (call.cyclesCount == 1) {
                    callType = "ВЫЗЫВАЕТСЯ ИЗ ЦИКЛА";
                } else {
                    callType = "ВЫЗЫВАЕТСЯ ИЗ " + call.cyclesCount + " ЦИКЛОВ";
                }
            } else {
                callType = "ВЫЗЫВАЕТСЯ ИЗ ФУНКЦИИ (без цикла)";
            }

            sb.append("[УРОВЕНЬ ").append(level).append("] ↑ ").append(callType).append("\n");
            sb.append("            └─ Функция: ").append(call.callerFunctionName);

            // Если есть циклы, добавляем информацию о них
            if (call.cyclesCount > 0) {
                sb.append(" (содержит ").append(call.cyclesCount).append(" цикл");
                if (call.cyclesCount > 1) sb.append("а");
                sb.append(")");
            }
            sb.append("\n\n");
        }

        // ПОДСЧЕТ СТАТИСТИКИ
        int totalCycles = (int) chain.stream()
                .filter(c -> c.cyclesCount > 0)
                .count();

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📊 СТАТИСТИКА:\n");
        sb.append("   • Количество циклов в цепочке: ").append(totalCycles).append("\n");
        sb.append("   • Глубина вложенности: ").append(chain.size()).append(" уровней\n");

        // ПОСТРОЕНИЕ ПОТОКА ВЫПОЛНЕНИЯ
        if (totalCycles > 0) {
            sb.append("\n🔄 ПОТОК ВЫПОЛНЕНИЯ:\n");
            sb.append("   ");

            // Строим цепочку от корня к запросу (обратный порядок)
            List<String> flowParts = new ArrayList<>();
            for (int i = chain.size() - 1; i >= 0; i--) {
                CallInLoop call = chain.get(i);
                if (call.cyclesCount > 0) {
                    flowParts.add("ЦИКЛ(" + call.callerFunctionName + ")");
                } else {
                    flowParts.add(call.callerFunctionName);
                }
            }
            flowParts.add("ЗАПРОС(" + query.functionName + ")");

            sb.append(String.join(" → ", flowParts));
            sb.append("\n");
        }

        // ВЫВОД ПРОБЛЕМЫ
        sb.append("\n⚠️  ПРОБЛЕМА:\n");
        if (totalCycles == 1) {
            sb.append("   • Запрос выполняется внутри 1 цикла\n");
            sb.append("   • При 1000 итераций → 1000 запросов к БД\n");
        } else if (totalCycles == 2) {
            sb.append("   • Запрос выполняется внутри 2 вложенных циклов!\n");
            sb.append("   • При 1000×1000 итераций → 1 000 000 запросов к БД!\n");
        } else if (totalCycles == 3) {
            sb.append("   • Запрос выполняется внутри 3 вложенных циклов!!!\n");
            sb.append("   • При 1000×1000×1000 итераций → 1 000 000 000 запросов!\n");
            sb.append("   • Это критическая проблема производительности!\n");
        } else if (totalCycles >= 4) {
            sb.append("   • Запрос выполняется внутри ").append(totalCycles).append(" вложенных циклов!\n");
            sb.append("   • Система может \"зависнуть\" на неопределенное время\n");
            sb.append("   • ТРЕБУЕТ НЕМЕДЛЕННОГО ИСПРАВЛЕНИЯ!\n");
        }

        return sb.toString();
    }


    // ========== СОЗДАНИЕ ISSUE ==========
    private static PerformanceIssue createIssue(QueryElement query, CallChainResult chainResult,
                                                Connection conn) throws SQLException {

        String sql = """
        SELECT mf.function_name, mf.start_line, 
               mm.object_full_name, mm.module_type, mm.file_path,
               mf.function_text
        FROM metadata_functions mf
        JOIN metadata_modules mm ON mf.module_id = mm.id
        WHERE mf.id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, query.functionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String functionName = rs.getString("function_name");
                int lineNumber = rs.getInt("start_line") + query.startLine - 1;
                String objectName = rs.getString("object_full_name");
                String moduleType = rs.getString("module_type");
                String filePath = rs.getString("file_path");
                String functionText = rs.getString("function_text");

                String severity, type, description;

                if (chainResult.totalCycles >= 4) {
                    severity = "CRITICAL";
                    type = "Запрос в 4+ циклах";
                    description = chainResult.totalCycles + " циклов через цепочку вызовов";
                } else if (chainResult.totalCycles == 3) {
                    severity = "HIGH";
                    type = "Запрос в 3 циклах";
                    description = "3 цикла через цепочку вызовов";
                } else {
                    severity = "MEDIUM";
                    type = "Запрос в 2 циклах";
                    description = "2 цикла через цепочку вызовов";
                }

                String problematicCode = extractLine(functionText, query.startLine);
                String recommendation = createRecommendation(chainResult, severity);

                PerformanceIssue issue = new PerformanceIssue(
                        type, severity, description,
                        functionName, objectName, moduleType,
                        filePath, problematicCode, lineNumber,
                        recommendation, chainResult.fullPath, chainResult.totalCycles,
                        query.functionId
                );

                issue.chainFunctionIds = chainResult.chainFunctionIds; // ← ПЕРЕДАЁМ ЦЕПОЧКУ

                return issue;
            }
        }

        return null;
    }

    private static String extractLine(String functionText, int startLine) {
        if (functionText == null || functionText.isEmpty()) return "";

        String[] lines = functionText.split("\n", -1);
        if (startLine > 0 && startLine <= lines.length) {
            String line = lines[startLine - 1].trim();
            return line.length() > 100 ? line.substring(0, 97) + "..." : line;
        }
        return "";
    }

    private static String createRecommendation(CallChainResult chainResult, String severity) {
        StringBuilder sb = new StringBuilder();

        sb.append("🔍 АНАЛИЗ ПРОБЛЕМЫ:\n");
        sb.append("Запрос выполняется внутри ").append(chainResult.totalCycles)
                .append(" вложенных циклов.\n");
        sb.append("При каждой итерации внешнего цикла запрос будет выполняться заново.\n\n");

        if (severity.equals("CRITICAL")) {
            sb.append("🔴 КРИТИЧЕСКАЯ ПРОБЛЕМА ПРОИЗВОДИТЕЛЬНОСТИ\n");
            sb.append("• Запрос выполняется в 4+ циклах\n");
            sb.append("• При 1000 итераций = 1000+ запросов к БД\n");
            sb.append("• Время выполнения может составлять минуты\n\n");
        } else if (severity.equals("HIGH")) {
            sb.append("🟠 ВЫСОКИЙ УРОВЕНЬ ПРОБЛЕМЫ\n");
            sb.append("• Запрос выполняется в 3 циклах\n");
            sb.append("• При 1000 итераций = 1000+ запросов к БД\n\n");
        } else {
            sb.append("🟡 СРЕДНИЙ УРОВЕНЬ ПРОБЛЕМЫ\n");
            sb.append("• Запрос выполняется в 2 циклах\n");
            sb.append("• При 1000 итераций = 1000+ запросов к БД\n\n");
        }

        sb.append("💡 РЕКОМЕНДАЦИИ ПО ИСПРАВЛЕНИЮ:\n");
        sb.append("1. Выполнить запрос один раз до начала цикла\n");
        sb.append("2. Сохранить результат во временную таблицу значений\n");
        sb.append("3. Использовать поиск по временной таблице внутри цикла\n");
        sb.append("4. Пример исправления:\n");
        sb.append("   // Вместо:\n");
        sb.append("   Для Каждого Строка Из Таблица Цикл\n");
        sb.append("       Запрос = Новый Запрос;\n");
        sb.append("       Запрос.Текст = \"...\";\n");
        sb.append("       Результат = Запрос.Выполнить();\n");
        sb.append("   КонецЦикла;\n\n");
        sb.append("   // Сделать:\n");
        sb.append("   Запрос = Новый Запрос;\n");
        sb.append("   Запрос.Текст = \"...\";\n");
        sb.append("   Результат = Запрос.Выполнить();\n");
        sb.append("   Для Каждого Строка Из Таблица Цикл\n");
        sb.append("       // Использовать Результат.Найти(...)\n");
        sb.append("   КонецЦикла;\n");

        return sb.toString();
    }

    // ========== СТАТИСТИКА ==========
    public static Map<String, Integer> getPerformanceStatistics(List<PerformanceIssue> issues) {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("TOTAL", issues.size());
        stats.put("CRITICAL", 0);
        stats.put("HIGH", 0);
        stats.put("MEDIUM", 0);

        for (PerformanceIssue issue : issues) {
            stats.put(issue.severity, stats.getOrDefault(issue.severity, 0) + 1);
        }
        return stats;
    }

    // ========== ТОЧКА ВХОДА ==========
    public static void main(String[] args) {
        System.out.println("⚡ Анализатор производительности 1С");
        System.out.println("=".repeat(60));

        List<PerformanceIssue> issues = scanForPerformanceIssues();
        Map<String, Integer> stats = getPerformanceStatistics(issues);

        System.out.println("\n📊 Результаты:");
        System.out.println("-".repeat(60));
        System.out.println("Всего проблем: " + stats.get("TOTAL"));
        System.out.println("CRITICAL: " + stats.getOrDefault("CRITICAL", 0));
        System.out.println("HIGH:     " + stats.getOrDefault("HIGH", 0));
        System.out.println("MEDIUM:   " + stats.getOrDefault("MEDIUM", 0));

        if (!issues.isEmpty()) {
            System.out.println("\n🔍 Примеры проблем:");
            System.out.println("-".repeat(60));
            for (int i = 0; i < Math.min(3, issues.size()); i++) {
                PerformanceIssue issue = issues.get(i);
                System.out.println((i + 1) + ". " + issue.functionName);
                System.out.println("   Циклов: " + issue.chainDepth + " | " + issue.objectFullName);
                String[] lines = issue.chainPath.split("\n");
                if (lines.length > 0) {
                    System.out.println("   Цепочка: " + lines[0]);
                }
            }
        }

        System.out.println("\n✅ Сканирование завершено");
    }
}