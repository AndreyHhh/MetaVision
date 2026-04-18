package com.lycurg.metavisionfor1c;

import javafx.scene.control.TreeItem;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.function.Consumer;

//# Загрузка метаданных и модулей конфигурации 1С в базу данных.
public class UnifiedDataLoader {
    private String configDir;
    private ModuleLoader_Version versionReader;
    private Consumer<String> messageConsumer;
    private Consumer<Double> progressCallback;

    public UnifiedDataLoader(String configDir) {
        this.configDir = configDir;
        this.messageConsumer = System.out::println;
    }

    public void setMessageConsumer(Consumer<String> messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    public void setProgressCallback(Consumer<Double> progressCallback) {
        this.progressCallback = progressCallback;
    }

    private void addMessage(String message) {
        messageConsumer.accept(message);
    }

    private void updateProgress(double progress) {
        if (progressCallback != null) progressCallback.accept(progress);
    }

    public void loadAllData() throws SQLException {
        addMessage("=== ЗАГРУЗКА КОНФИГУРАЦИИ ===");
        updateProgress(0.01);

        try {



            addMessage("📊 Оптимизация базы данных...");
            MetadataDbManager.disableIndexes();  // ← выключаем индексы
            updateProgress(0.05);

            addMessage("📊 Инициализация таблиц...");
            MetadataDbManager.initializeMetadataTables();
            updateProgress(0.08);

            addMessage("🌲 Сохранение дерева конфигурации...");
            TreeItem<String> configTree = new ConfigTreeManager().buildConfigTree(configDir + "/Configuration.xml");
            SaveInKonfig.save(configTree);
            addMessage("✅ Дерево конфигурации сохранено");
            updateProgress(0.12);

            addMessage("📦 Загрузка объектов и модулей...");
            loadAllObjectsAndModules();  // ← загружаем БЕЗ индексов
            updateProgress(0.70);

            addMessage("🔧 Включаем индексы...");
            MetadataDbManager.enableIndexes();  // ← включаем ПОСЛЕ загрузки

            addMessage("🔧 Оптимизация БД...");
            MetadataDbManager.optimizeDatabase();
            MetadataDbManager.vacuumDatabase();
            addMessage("✅ База данных оптимизирована");
            updateProgress(0.80);

            addMessage("🔍 Запуск анализа кода и детекции рекурсий...");
            analyzeFunctionsAndElements();  // ← анализ с индексами


            updateProgress(1.0);
            addMessage("✅ ВСЕ ЭТАПЫ ЗАВЕРШЕНЫ УСПЕШНО");

        } catch (Exception e) {
            addMessage("❌ ОШИБКА: " + e.getMessage());
            try { MetadataDbManager.enableIndexes(); } catch (SQLException ex) { /* ignore */ }
            throw new SQLException(e.getMessage(), e);
        } finally {
            cleanupResources();
        }
    }

    // ============================================================================
    // 📦 ЗАГРУЗКА ОБЪЕКТОВ И МОДУЛЕЙ
    // ============================================================================

    private void loadAllObjectsAndModules() {
        String[] allObjectTypes = {
                "Documents", "Catalogs", "InformationRegisters", "AccumulationRegisters",
                "AccountingRegisters", "CalculationRegisters",          // ← добавить
                "Reports", "DataProcessors", "Enums", "Constants",
                "ExchangePlans", "DocumentJournals", "SettingsStorages",
                "WebServices", "HTTPServices", "CommonModules", "CommonForms", "CommonCommands",
                "FilterCriteria", "ChartsOfAccounts", "ChartsOfCharacteristicTypes",
                "ChartsOfCalculationTypes",                             // ← добавить
                "BusinessProcesses", "Tasks"
        };

        int totalSuccess = 0;
        int totalErrors  = 0;

        for (String objectType : allObjectTypes) {
            ObjectLoadResult result = loadObjectsByType(objectType);
            totalSuccess += result.successCount;
            totalErrors  += result.errorCount;
            if (result.successCount > 0 || result.errorCount > 0) {
                addMessage("✅ " + objectType + " — успешно: " + result.successCount
                        + ", ошибок: " + result.errorCount);
            }
        }

        addMessage("=== ИТОГО: " + totalSuccess + " объектов, ошибок: " + totalErrors + " ===");
    }

    // Загружает все объекты одного типа в одной транзакции через одно соединение.
    private ObjectLoadResult loadObjectsByType(String objectType) {
        ObjectLoadResult result = new ObjectLoadResult();
        List<File> objectDirs = getObjectDirectories(objectType, configDir);

        if (objectDirs.isEmpty()) return result;

        Map<String, String> versionsMap = preloadVersionsForType(objectType, objectDirs);

        // Одно соединение + одна транзакция на весь тип объекта
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            applyFastPragmas(conn);
            conn.setAutoCommit(false);

            try {
                for (File objectDir : objectDirs) {
                    try {
                        processSingleObject(objectType, objectDir, versionsMap, conn);
                        result.successCount++;
                    } catch (Exception e) {
                        result.errorCount++;
                        System.err.println("❌ Ошибка " + objectDir.getName() + ": " + e.getMessage());
                    }
                }
                conn.commit();
            } catch (Exception e) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
                addMessage("❌ Ошибка загрузки типа " + objectType + ": " + e.getMessage());
            }

        } catch (SQLException e) {
            addMessage("❌ Не удалось открыть соединение для " + objectType + ": " + e.getMessage());
        }

        return result;
    }

    private void processSingleObject(String objectType, File objectDir,
                                     Map<String, String> versionsMap, Connection conn) throws SQLException {
        String objectName  = objectDir.getName();
        String fullNameRu  = translateObjectTypeToRussian(objectType) + "." + objectName;
        String configVersion = versionsMap.get(fullNameRu);

        int objectId = MetadataDbManager.saveObject(conn, objectType, objectName, fullNameRu, configVersion);

        if (objectDir.exists() && objectDir.isDirectory()) {
            ModuleLoader_bsl.saveObjectModules(objectId, fullNameRu, objectDir, objectType, conn);
        }
    }

    private static void applyFastPragmas(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA busy_timeout = 60000");
            st.execute("PRAGMA synchronous = NORMAL");
            st.execute("PRAGMA cache_size = -256000");
            st.execute("PRAGMA temp_store = MEMORY");
        }
    }

    private List<File> getObjectDirectories(String objectType, String configDir) {
        List<File> objectEntries = new ArrayList<>();
        File typeDir = new File(configDir + "/" + objectType);
        if (!typeDir.exists()) return objectEntries;

        File[] children = typeDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) objectEntries.add(child);
            }
        }
        return objectEntries;
    }

    private Map<String, String> preloadVersionsForType(String objectType, List<File> objectDirs) {
        if (versionReader == null) return Collections.emptyMap();

        List<String> allObjectNames = new ArrayList<>();
        for (File objectDir : objectDirs) {
            allObjectNames.add(translateObjectTypeToRussian(objectType) + "." + objectDir.getName());
        }
        return versionReader.getVersionsForObjects(allObjectNames);
    }

    private String translateObjectTypeToRussian(String objectType) {
        Map<String, String> typeMap = Map.ofEntries(
                Map.entry("Documents",                   "Документ"),
                Map.entry("Catalogs",                    "Справочник"),
                Map.entry("InformationRegisters",        "РегистрСведений"),
                Map.entry("AccumulationRegisters",       "РегистрНакопления"),
                Map.entry("AccountingRegisters",         "РегистрБухгалтерии"),
                Map.entry("DocumentJournals",            "ЖурналДокументов"),
                Map.entry("ExchangePlans",               "ПланОбмена"),
                Map.entry("SettingsStorages",            "ХранилищеНастроек"),
                Map.entry("WebServices",                 "ВебСервис"),
                Map.entry("HTTPServices",                "HTTPService"),
                Map.entry("Enums",                       "Перечисление"),
                Map.entry("Constants",                   "Константа"),
                Map.entry("Reports",                     "Отчет"),
                Map.entry("DataProcessors",              "Обработка"),
                Map.entry("CommonModules",               "ОбщийМодуль"),
                Map.entry("CommonForms",                 "ОбщаяФорма"),
                Map.entry("CommonCommands",              "ОбщаяКоманда"),

                Map.entry("CalculationRegisters",        "РегистрРасчетов"),
                Map.entry("ChartsOfCalculationTypes",    "ПланВидовРасчета"),
                Map.entry("BusinessProcesses",           "БизнесПроцесс"),
                Map.entry("Tasks",                       "Задача"),
                Map.entry("FilterCriteria",              "КритерийОтбора")

        );
        return typeMap.getOrDefault(objectType, objectType);
    }

    // ============================================================================
    // 🔍 АНАЛИЗ КОДА И ДЕТЕКЦИЯ РЕКУРСИЙ
    // ============================================================================

    private void analyzeFunctionsAndElements() {
        addMessage("🔍 Запуск анализа кода...");

        CodeAnalysisEngine_base engine = new CodeAnalysisEngine_base();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        try {
            List<Integer> moduleIds = getAllModuleIdsFromDatabase();
            if (moduleIds.isEmpty()) {
                addMessage("⚠️ Модули не найдены в БД!");
                return;
            }

            addMessage("📊 Найдено модулей для анализа: " + moduleIds.size());

            engine.analyzeAllModulesWithGuarantee(moduleIds);
            addMessage("✅ Анализ кода завершен!");

            // 🔥 ПОСТ-СВЯЗЫВАНИЕ
            addMessage("🔗 Пост-связывание вызовов функций...");
            try {
                engine.postLinkFunctionCalls();
                addMessage("✅ Пост-связывание завершено!");
            } catch (SQLException e) {
                addMessage("❌ SQL ошибка: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                addMessage("❌ Ошибка: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace();
            }

            addMessage("🔁 Детекция рекурсивных функций...");
            try {
                detectRecursiveFunctions();
                addMessage("✅ Детекция рекурсий завершена!");
            } catch (SQLException e) {
                addMessage("❌ Ошибка детекции рекурсий: " + e.getMessage());
                e.printStackTrace();
            }

            addMessage("🔧 Переиндексация после связывания...");
            try {
                MetadataDbManager.reindexAfterLinking();
                addMessage("✅ Переиндексация завершена!");
            } catch (SQLException e) {
                addMessage("❌ Ошибка переиндексации: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            addMessage("❌ Ошибка анализа кода: " + e.getMessage());
            e.printStackTrace();
        } finally {
            engine.cleanup();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            addMessage("🧠 Использовано памяти: " + ((memoryAfter - memoryBefore) / 1024 / 1024) + "MB");
            System.gc();
        }
    }

    private List<Integer> getAllModuleIdsFromDatabase() {
        List<Integer> moduleIds = new ArrayList<>();
        String sql = "SELECT id FROM metadata_modules ORDER BY id";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) moduleIds.add(rs.getInt("id"));
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения модулей: " + e.getMessage());
        }
        return moduleIds;
    }

    // ============================================================================
    // 🔁 ДЕТЕКЦИЯ РЕКУРСИВНЫХ ФУНКЦИЙ
    // ============================================================================

    //находим рекурсивные функии.
    private void detectRecursiveFunctions() throws SQLException {
        addMessage("  🔁 Детекция рекурсивных функций...");
        long startTime = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            // Шаг 1: Прямая рекурсия (быстро)
            addMessage("    📍 Шаг 1: Прямая рекурсия...");
            int directCount = 0;
            try (Statement stmt = conn.createStatement()) {
                directCount = stmt.executeUpdate("""
                INSERT OR REPLACE INTO recursive_functions
                    (function_id, function_name, module_id, object_full_name,
                     recursion_type, recursion_chain)
                SELECT DISTINCT
                    f.id, f.function_name, f.module_id, m.object_full_name,
                    'DIRECT', f.function_name
                FROM code_elements ce
                JOIN metadata_functions f ON ce.function_id = f.id
                JOIN metadata_modules m ON f.module_id = m.id
                WHERE ce.element_type = 'ВызовФункции'
                  AND ce.called_function_id = ce.function_id
                  AND ce.called_function_id IS NOT NULL
                """);
            }
            addMessage("      ✅ Прямая рекурсия: " + directCount);

            // Шаг 2: Косвенная рекурсия через Tarjan (только если есть вызовы)
            addMessage("    📍 Шаг 2: Косвенная рекурсия...");

            // Загружаем граф вызовов ТОЛЬКО для функций, у которых есть вызовы
            Map<Integer, String> functionNames = new HashMap<>();
            Map<Integer, Set<Integer>> callGraph = new HashMap<>();
            Set<Integer> allFunctions = new HashSet<>();

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT DISTINCT ce.function_id, ce.called_function_id, f.function_name "
                                 + "FROM code_elements ce "
                                 + "JOIN metadata_functions f ON ce.function_id = f.id "
                                 + "WHERE ce.element_type = 'ВызовФункции' "
                                 + "AND ce.called_function_id IS NOT NULL "
                                 + "AND ce.function_id != ce.called_function_id")) {
                while (rs.next()) {
                    int caller = rs.getInt("function_id");
                    int callee = rs.getInt("called_function_id");
                    String funcName = rs.getString("function_name");

                    allFunctions.add(caller);
                    allFunctions.add(callee);
                    functionNames.putIfAbsent(caller, funcName);
                    functionNames.putIfAbsent(callee, "");
                    callGraph.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
                }
            }

            if (callGraph.isEmpty()) {
                addMessage("      ✅ Нет вызовов для анализа косвенной рекурсии");
                return;
            }

            addMessage("      📊 Граф вызовов: " + callGraph.size() + " функций");

            // Поиск SCC (циклов)
            List<Set<Integer>> sccs = findSccsTarjan(callGraph, allFunctions);

            // Сохраняем косвенную рекурсию
            int indirectCount = 0;
            try (PreparedStatement ps = conn.prepareStatement("""
            INSERT OR REPLACE INTO recursive_functions
                (function_id, function_name, module_id, object_full_name,
                 recursion_type, recursion_chain)
            SELECT f.id, f.function_name, f.module_id, m.object_full_name,
                   'INDIRECT', ?
            FROM metadata_functions f
            JOIN metadata_modules m ON f.module_id = m.id
            WHERE f.id = ?
            """)) {

                conn.setAutoCommit(false);
                for (Set<Integer> scc : sccs) {
                    if (scc.size() <= 1) continue;

                    // Строим цепочку для цикла
                    String chain = buildCycleString(scc, functionNames);

                    for (int funcId : scc) {
                        // Проверяем, нет ли уже прямой рекурсии
                        try (Statement st = conn.createStatement();
                             ResultSet rs = st.executeQuery(
                                     "SELECT id FROM recursive_functions WHERE function_id = " + funcId)) {
                            if (rs.next()) continue;
                        }

                        ps.setString(1, chain);
                        ps.setInt(2, funcId);
                        ps.addBatch();
                        indirectCount++;

                        if (indirectCount % 500 == 0) {
                            ps.executeBatch();
                            conn.commit();
                        }
                    }
                }
                ps.executeBatch();
                conn.commit();
            }

            addMessage("      ✅ Косвенная рекурсия: " + indirectCount);

        } catch (SQLException e) {
            addMessage("    ❌ Ошибка детекции рекурсий: " + e.getMessage());
            throw e;
        }

        long endTime = System.currentTimeMillis();
        addMessage("  ✅ Детекция рекурсий завершена за " + (endTime - startTime) + "ms");
    }

    private String buildCycleString(Set<Integer> scc, Map<Integer, String> functionNames) {
        StringBuilder sb = new StringBuilder();
        List<Integer> sorted = new ArrayList<>(scc);
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(" → ");
            String name = functionNames.getOrDefault(sorted.get(i), "func_" + sorted.get(i));
            sb.append(name);
        }
        if (sb.length() > 500) {
            return sb.substring(0, 497) + "...";
        }
        return sb.toString();
    }

    private List<Set<Integer>> findSccsTarjan(Map<Integer, Set<Integer>> graph, Set<Integer> allNodes) {
        List<Set<Integer>> result = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>();
        Map<Integer, Integer> lowlink = new HashMap<>();
        Set<Integer> onStack = new HashSet<>();
        Deque<Integer> stack = new ArrayDeque<>();
        int[] counter = new int[1];

        for (int node : allNodes) {
            if (!index.containsKey(node)) {
                strongConnect(node, graph, index, lowlink, onStack, stack, counter, result);
            }
        }

        return result;
    }

    private void strongConnect(int v, Map<Integer, Set<Integer>> graph,
                               Map<Integer, Integer> index, Map<Integer, Integer> lowlink,
                               Set<Integer> onStack, Deque<Integer> stack, int[] counter,
                               List<Set<Integer>> result) {
        index.put(v, counter[0]);
        lowlink.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.add(v);

        for (int w : graph.getOrDefault(v, Collections.emptySet())) {
            if (!index.containsKey(w)) {
                strongConnect(w, graph, index, lowlink, onStack, stack, counter, result);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            Set<Integer> scc = new HashSet<>();
            int w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (w != v);
            if (scc.size() > 1) {
                result.add(scc);
            }
        }
    }

    // ============================================================================
    // 🔁 Косвенная рекурсия — алгоритм Тарьяна
    // ============================================================================

    private void detectIndirectRecursion() throws SQLException {
        String dbUrl = DBPathHelper.getDbUrl();

        Map<Integer, String>      functionNames    = new HashMap<>();
        Map<Integer, Set<Integer>> callGraph       = new HashMap<>();
        Set<Integer>              directRecursionIds = new HashSet<>();

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, function_name FROM metadata_functions")) {
                while (rs.next()) functionNames.put(rs.getInt(1), rs.getString(2));
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT function_id FROM recursive_functions WHERE recursion_type = 'DIRECT'")) {
                while (rs.next()) directRecursionIds.add(rs.getInt(1));
            }

            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("""
                     SELECT DISTINCT function_id, called_function_id
                     FROM code_elements
                     WHERE element_type = 'ВызовФункции'
                       AND called_function_id IS NOT NULL
                       AND function_id != called_function_id
                     """)) {
                while (rs.next()) {
                    int caller = rs.getInt(1);
                    int callee = rs.getInt(2);
                    callGraph.computeIfAbsent(caller, k -> new HashSet<>()).add(callee);
                }
            }
        }

        addMessage("    📊 Граф вызовов: " + callGraph.size() + " функций");

        List<Set<Integer>> sccs = tarjanSCC(callGraph);

        List<Object[]> toInsert = new ArrayList<>();
        for (Set<Integer> scc : sccs) {
            if (scc.size() <= 1) continue;
            String cycle = findCycleInScc(scc, callGraph, functionNames);
            for (Integer funcId : scc) {
                if (!directRecursionIds.contains(funcId)) {
                    toInsert.add(new Object[]{funcId, cycle});
                }
            }
        }

        if (!toInsert.isEmpty()) {
            String insertSql = """
                INSERT OR REPLACE INTO recursive_functions
                    (function_id, function_name, module_id, object_full_name,
                     recursion_type, recursion_chain)
                SELECT f.id, f.function_name, f.module_id, m.object_full_name,
                       'INDIRECT', ?
                FROM metadata_functions f
                JOIN metadata_modules m ON f.module_id = m.id
                WHERE f.id = ?
                """;

            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement ps = conn.prepareStatement(insertSql)) {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA busy_timeout = 30000");
                }
                for (Object[] row : toInsert) {
                    ps.setString(1, (String) row[1]);
                    ps.setInt(2, (Integer) row[0]);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            }
        }

        addMessage("    ✅ Найдено косвенной рекурсии: " + toInsert.size() + " функций");
    }

    private String findCycleInScc(Set<Integer> scc, Map<Integer, Set<Integer>> callGraph,
                                  Map<Integer, String> functionNames) {
        Integer start = scc.iterator().next();
        Map<Integer, Integer> parent = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        parent.put(start, null);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int neighbor : callGraph.getOrDefault(current, Collections.emptySet())) {
                if (!scc.contains(neighbor)) continue;
                if (neighbor == start && current != start) {
                    return buildCyclePath(start, current, parent, functionNames);
                }
                if (!parent.containsKey(neighbor)) {
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }
        return buildSccChain(scc, functionNames);
    }

    private String buildCyclePath(int start, int end, Map<Integer, Integer> parent,
                                  Map<Integer, String> functionNames) {
        List<String> path = new ArrayList<>();
        int current = end;
        while (current != start) {
            path.add(functionNames.getOrDefault(current, "func_" + current));
            Integer p = parent.get(current);
            if (p == null) break;
            current = p;
        }
        path.add(functionNames.getOrDefault(start, "func_" + start));
        Collections.reverse(path);
        path.add(functionNames.getOrDefault(start, "func_" + start));
        String chain = String.join(" → ", path);
        return chain.length() > 500 ? chain.substring(0, 497) + "..." : chain;
    }

    private List<Set<Integer>> tarjanSCC(Map<Integer, Set<Integer>> graph) {
        List<Set<Integer>> result = new ArrayList<>();
        Set<Integer> allNodes = new HashSet<>(graph.keySet());
        for (Set<Integer> neighbors : graph.values()) allNodes.addAll(neighbors);

        Map<Integer, Integer> indexMap   = new HashMap<>();
        Map<Integer, Integer> lowlinkMap = new HashMap<>();
        Set<Integer>          onStack    = new HashSet<>();
        Deque<Integer>        sccStack   = new ArrayDeque<>();
        Map<Integer, Iterator<Integer>> iterMap = new HashMap<>();
        int[] counter = {0};

        for (Integer startNode : allNodes) {
            if (indexMap.containsKey(startNode)) continue;

            Deque<Integer> dfsStack = new ArrayDeque<>();
            indexMap.put(startNode, counter[0]);
            lowlinkMap.put(startNode, counter[0]);
            counter[0]++;
            sccStack.push(startNode);
            onStack.add(startNode);
            dfsStack.push(startNode);
            iterMap.put(startNode, graph.getOrDefault(startNode, Collections.emptySet()).iterator());

            while (!dfsStack.isEmpty()) {
                int v = dfsStack.peek();
                Iterator<Integer> it = iterMap.get(v);

                if (it.hasNext()) {
                    int w = it.next();
                    if (!indexMap.containsKey(w)) {
                        indexMap.put(w, counter[0]);
                        lowlinkMap.put(w, counter[0]);
                        counter[0]++;
                        sccStack.push(w);
                        onStack.add(w);
                        dfsStack.push(w);
                        iterMap.put(w, graph.getOrDefault(w, Collections.emptySet()).iterator());
                    } else if (onStack.contains(w)) {
                        lowlinkMap.put(v, Math.min(lowlinkMap.get(v), indexMap.get(w)));
                    }
                } else {
                    dfsStack.pop();
                    if (!dfsStack.isEmpty()) {
                        int parent = dfsStack.peek();
                        lowlinkMap.put(parent, Math.min(lowlinkMap.get(parent), lowlinkMap.get(v)));
                    }
                    if (lowlinkMap.get(v).equals(indexMap.get(v))) {
                        Set<Integer> scc = new HashSet<>();
                        int w;
                        do {
                            w = sccStack.pop();
                            onStack.remove(w);
                            scc.add(w);
                        } while (w != v);
                        result.add(scc);
                    }
                }
            }
        }
        return result;
    }

    private String buildSccChain(Set<Integer> scc, Map<Integer, String> functionNames) {
        StringBuilder sb = new StringBuilder();
        scc.stream()
                .map(id -> functionNames.getOrDefault(id, "func_" + id))
                .sorted()
                .forEach(name -> { if (sb.length() > 0) sb.append(" ↔ "); sb.append(name); });
        String chain = sb.toString();
        return chain.length() > 500 ? chain.substring(0, 497) + "..." : chain;
    }

    // ============================================================================
    // 🧹 ОЧИСТКА РЕСУРСОВ
    // ============================================================================

    private void cleanupResources() {
        if (versionReader != null) {
            versionReader.dispose();
            versionReader = null;
        }
        System.gc();
    }

    private static class ObjectLoadResult {
        int successCount = 0;
        int errorCount   = 0;
    }
}