package com.lycurg.metavisionfor1c;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

//# Основной движок многопоточного парсинга функций и элементов кода 1С
public class CodeAnalysisEngine_base {


    // ========== КОНФИГУРАЦИЯ ==========
    private final int THREAD_COUNT  = Math.max(4, Runtime.getRuntime().availableProcessors());
    private final int BATCH_SIZE    = 500;
    // Сколько модулей обрабатываем за один конвейерный проход.
    // Не держим всё в RAM — при 100 ГБ исходников это критично.
    private final int PIPELINE_CHUNK = 1000;

    // ========== CONNECTION POOL (ThreadLocal) ==========
    private static final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();

    private Connection getConnection() throws SQLException {
        Connection conn = threadConnection.get();
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA busy_timeout = 10000");
                stmt.execute("PRAGMA journal_mode = WAL");
                stmt.execute("PRAGMA synchronous = NORMAL");
                stmt.execute("PRAGMA cache_size = -500000");
                stmt.execute("PRAGMA temp_store = MEMORY");
                stmt.execute("PRAGMA mmap_size = 536870912");
            }
            threadConnection.set(conn);
        }
        return conn;
    }

    private void closeThreadConnection() {
        Connection conn = threadConnection.get();
        if (conn != null) {
            try { conn.close(); } catch (SQLException e) { /* ignore */ }
            threadConnection.remove();
        }
    }

    // ========== ИСПОЛНИТЕЛИ ==========
    private ExecutorService moduleExecutor;

    // ========== ТРЕКЕРЫ ПРОГРЕССА ==========
    private volatile int totalModules;
    private final AtomicInteger processedModules = new AtomicInteger(0);
    private final AtomicInteger failedModules    = new AtomicInteger(0);
    private volatile boolean shutdownRequested   = false;
    private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

    // ========== ОСНОВНОЙ МЕТОД ==========

    public void analyzeAllModulesWithGuarantee(List<Integer> allModuleIds) {
        System.out.println("🔴 НАЧАЛО АНАЛИЗА. Модулей: " + allModuleIds.size());

        if (moduleExecutor != null) {
            moduleExecutor.shutdownNow();
            moduleExecutor = null;
        }

        moduleExecutor = Executors.newFixedThreadPool(THREAD_COUNT);

        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        totalModules = allModuleIds.size();
        processedModules.set(0);
        failedModules.set(0);
        errors.clear();

        try {
            // Конвейер: обрабатываем пачками, не держим всё в RAM
            int totalChunks = (int) Math.ceil((double) allModuleIds.size() / PIPELINE_CHUNK);

            for (int chunkIdx = 0; chunkIdx < totalChunks; chunkIdx++) {
                int from = chunkIdx * PIPELINE_CHUNK;
                int to   = Math.min(from + PIPELINE_CHUNK, allModuleIds.size());
                List<Integer> chunkIds = allModuleIds.subList(from, to);

                System.out.println("📦 Пачка " + (chunkIdx + 1) + "/" + totalChunks
                        + " (" + from + "–" + to + ")");

                // Этап 1: параллельно парсим функции пачки
                List<FunctionData> functions = parseAllFunctions(chunkIds);

                // Этап 2: пишем функции в БД
                saveAllFunctionsToDatabase(functions);

                // Этап 3: параллельно анализируем элементы
                List<CodeElementData> elements = analyzeAllElements(functions);

                // Освобождаем функции — они больше не нужны
                functions.clear();
                functions = null;
                System.gc();

                // Этап 4: пишем элементы в БД
                saveAllElementsToDatabase(elements);

                elements.clear();
                elements = null;
                System.gc();

                System.out.println("✅ Пачка " + (chunkIdx + 1) + " завершена");
            }

            //Все пакеты сформировались.



            // Этап 5: обновляем function_id для всех элементов (один раз после всех пачек)
            System.out.println("🔄 Обновление function_id...");
            updateFunctionIds();

            // Этап 6: связываем вызовы функций через HashMap (без N+1)
        //    System.out.println("🔄 Связывание вызовов функций...");
         //   updateFunctionCallsViaHashMap();

            // Этап 7: связываем owner_id через SQL (без Java-цикла)
            System.out.println("🔄 Связывание owner_id...");
            updateOwnerIdsMultithreaded();

            System.out.println("🎉 АНАЛИЗ ЗАВЕРШЁН. Модулей обработано: " + processedModules.get()
                    + ", ошибок: " + failedModules.get());

        } catch (Exception e) {
            System.err.println("❌ КРИТИЧЕСКАЯ ОШИБКА АНАЛИЗА: " + e.getMessage());
            cleanupDatabase();
            throw new RuntimeException("АНАЛИЗ ПРЕРВАН: " + e.getMessage(), e);
        } finally {
            shutdownExecutors();
            CodeAnalyzer.clearCache();
            closeThreadConnection();

            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            System.out.println("🧠 Памяти использовано: "
                    + ((memoryAfter - memoryBefore) / 1024 / 1024) + " MB");
        }
    }



    /**
     * Пост-связывание вызовов функций ПОСЛЕ загрузки всех модулей
     * Вызывать только когда все функции уже в БД
     */
    public void postLinkFunctionCalls() throws SQLException {
        System.out.println("=== postLinkFunctionCalls STARTED ===");
        System.out.println("🔗 ПОСТ-СВЯЗЫВАНИЕ ВЫЗОВОВ ФУНКЦИЙ");
        long startTime = System.currentTimeMillis();

        // 1. Загружаем все функции в HashMap
        Map<String, Integer> funcByModuleAndName = new HashMap<>();
        Map<String, Integer> funcByObjectAndName = new HashMap<>();

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT mf.id, mf.module_id, mf.function_name, mm.object_full_name "
                             + "FROM metadata_functions mf "
                             + "JOIN metadata_modules mm ON mf.module_id = mm.id")) {
            while (rs.next()) {
                int funcId = rs.getInt("id");
                int moduleId = rs.getInt("module_id");
                String functionName = rs.getString("function_name");
                String objectFullName = rs.getString("object_full_name");

                funcByModuleAndName.put(moduleId + ":" + functionName, funcId);

                if (objectFullName != null && objectFullName.contains(".")) {
                    String shortName = objectFullName.substring(objectFullName.lastIndexOf('.') + 1);
                    funcByObjectAndName.putIfAbsent(shortName + ":" + functionName, funcId);
                }
            }
        }

        System.out.println("  📊 Загружено функций в индекс: " + funcByModuleAndName.size());

        List<int[]> toUpdate = new ArrayList<>();
        int totalCalls = 0;

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, element_name, element_text, module_id FROM code_elements "
                             + "WHERE element_type = 'ВызовФункции' AND called_function_id IS NULL")) {
            while (rs.next()) {
                totalCalls++;
                int elementId = rs.getInt("id");
                String funcName = rs.getString("element_name");
                String elementText = rs.getString("element_text");
                int moduleId = rs.getInt("module_id");

                Integer funcId = null;
                String qualifier = extractQualifier(elementText, funcName);

                if (qualifier == null) {
                    funcId = funcByModuleAndName.get(moduleId + ":" + funcName);
                } else {
                    funcId = funcByObjectAndName.get(qualifier + ":" + funcName);
                }

                if (funcId != null) {
                    toUpdate.add(new int[]{elementId, funcId});
                }
            }
        }

        System.out.println("  📊 Всего вызовов: " + totalCalls + ", найдено связей: " + toUpdate.size());

        // 2. Обновляем батчем
        if (!toUpdate.isEmpty()) {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE code_elements SET called_function_id = ? WHERE id = ?")) {
                conn.setAutoCommit(false);
                int batch = 0;
                for (int[] row : toUpdate) {
                    ps.setInt(1, row[1]);
                    ps.setInt(2, row[0]);
                    ps.addBatch();
                    if (++batch % 500 == 0) {
                        ps.executeBatch();
                        conn.commit();
                    }
                }
                ps.executeBatch();
                conn.commit();
            }
        }

        // 🔥 3. УДАЛЯЕМ МУСОР - вызовы, которые не удалось связать
       try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(true);

            int deleted = stmt.executeUpdate("""
                    DELETE FROM code_elements\s
                    WHERE element_type = 'ВызовФункции'\s
                      AND called_function_id IS NULL
    """);


            System.out.println("  🧹 Удалено мусорных вызовов (без связи): " + deleted);
        }

        long endTime = System.currentTimeMillis();
        System.out.println("  ✅ Пост-связывание завершено за " + (endTime - startTime) + "ms");
        System.out.println("  ✅ Обновлено вызовов: " + toUpdate.size());
    }



    /**
     * Извлекает квалификатор из текста вызова
     * "МойМодуль.МояФункция(" -> "МойМодуль"
     * "Документы.ТН.МодульОбъекта.Функция(" -> "МодульОбъекта"
     * "МояФункция(" -> null
     */
    private String extractQualifier(String elementText, String elementName) {
        if (elementText == null || elementText.trim().isEmpty()) return null;
        if (elementName == null || elementName.trim().isEmpty()) return null;

        String text = elementText.trim();

        // Ищем паттерн ".ИмяФункции(" напрямую
        String pattern = "." + elementName + "(";
        int idx = text.indexOf(pattern);
        if (idx == -1) return null;

        // Берём всё до точки
        String before = text.substring(0, idx);

        // Последний идентификатор перед точкой — это и есть квалификатор
        String[] parts = before.split("[^а-яА-ЯёЁa-zA-Z0-9_]");
        if (parts.length == 0) return null;

        String qualifier = parts[parts.length - 1];
        return qualifier.isEmpty() ? null : qualifier;
    }

    // ========== ЭТАП 1: ПАРАЛЛЕЛЬНЫЙ ПАРСИНГ ФУНКЦИЙ ==========

    private List<FunctionData> parseAllFunctions(List<Integer> moduleIds) throws Exception {
        List<FunctionData> allFunctions = Collections.synchronizedList(new ArrayList<>());
        List<String> moduleErrors       = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures         = new ArrayList<>();

        for (Integer moduleId : moduleIds) {
            if (shutdownRequested) break;
            futures.add(moduleExecutor.submit(() -> {
                try {
                    List<FunctionData> funcs = parseModuleFunctions(moduleId);
                    allFunctions.addAll(funcs);
                    processedModules.incrementAndGet();
                } catch (Exception e) {
                    failedModules.incrementAndGet();
                    moduleErrors.add("Модуль " + moduleId + ": " + e.getMessage());
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(); } catch (ExecutionException e) { /* уже в moduleErrors */ }
        }

        if (!moduleErrors.isEmpty()) {
            moduleErrors.forEach(err -> System.err.println("  ❌ " + err));
            throw new Exception("Парсинг завершён с " + moduleErrors.size() + " ошибками");
        }

        return allFunctions;
    }

    // ========== ЭТАП 2: ЗАПИСЬ ФУНКЦИЙ В БД ==========

    private void saveAllFunctionsToDatabase(List<FunctionData> functions) throws SQLException {
        if (functions.isEmpty()) return;

        String sql = "INSERT INTO metadata_functions "
                + "(module_id, function_name, function_type, function_text, function_text_find, start_line, end_line) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        Connection conn = getConnection();
        conn.setAutoCommit(false);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int batch = 0;
            for (FunctionData func : functions) {
                ps.setInt(1, func.moduleId);
                ps.setString(2, func.functionName);
                ps.setString(3, func.functionType);
                ps.setString(4, func.functionText);
                ps.setString(5, func.functionText != null ? func.functionText.toLowerCase() : "");
                ps.setInt(6, func.startLine);
                ps.setInt(7, func.endLine);
                ps.addBatch();

                if (++batch % BATCH_SIZE == 0) ps.executeBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw new SQLException("Ошибка записи функций: " + e.getMessage(), e);
        }
    }

    // ========== ЭТАП 3: ПАРАЛЛЕЛЬНЫЙ АНАЛИЗ ЭЛЕМЕНТОВ ==========

    private List<CodeElementData> analyzeAllElements(List<FunctionData> functions) throws Exception {
        List<CodeElementData> allElements = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean hasErrors           = new AtomicBoolean(false);
        List<Future<?>> futures           = new ArrayList<>();

        int chunkSize = Math.max(1, functions.size() / THREAD_COUNT);

        for (int i = 0; i < functions.size(); i += chunkSize) {
            final List<FunctionData> chunk =
                    functions.subList(i, Math.min(i + chunkSize, functions.size()));

            futures.add(moduleExecutor.submit(() -> {
                if (hasErrors.get()) return;
                List<CodeElementData> local = new ArrayList<>();
                for (FunctionData func : chunk) {
                    try {
                        CodeAnalyzer.CodeStructureDetailed structure =
                                CodeAnalyzer.analyzeFunctionStructure(func.functionText, func.functionName);
                        for (CodeElement element : structure.elements) {
                            local.add(createCodeElementData(func, element));
                        }
                    } catch (Exception e) {
                        hasErrors.set(true);
                        System.err.println("❌ Ошибка анализа функции "
                                + func.functionName + ": " + e.getMessage());
                        return;
                    }
                }
                allElements.addAll(local);
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(); } catch (ExecutionException e) { /* уже залогировано */ }
        }

        if (hasErrors.get()) throw new Exception("Анализ элементов прерван из-за ошибок");

        return allElements;
    }

    // ========== ЭТАП 4: ЗАПИСЬ ЭЛЕМЕНТОВ В БД ==========

    private void saveAllElementsToDatabase(List<CodeElementData> elements) throws SQLException {
        if (elements.isEmpty()) return;

        String sql = "INSERT INTO code_elements "
                + "(module_id, function_id, function_name, element_name, "
                + "element_type, owner_name, owner_type, owner_id, "
                + "start_line, end_line, element_text) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = getConnection();
        conn.setAutoCommit(false);

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int batch = 0;
            for (CodeElementData elem : elements) {
                ps.setInt(1, elem.moduleId);
                ps.setInt(2, elem.functionId);
                ps.setString(3, elem.functionName);
                ps.setString(4, elem.elementName);
                ps.setString(5, elem.elementType);
                ps.setString(6, elem.ownerName);
                ps.setString(7, elem.ownerType);
                ps.setObject(8, elem.ownerId);
                ps.setInt(9, elem.startLine);
                ps.setInt(10, elem.endLine);
                ps.setString(11, elem.elementText);
                ps.addBatch();

                if (++batch % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                    batch = 0;
                }
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw new SQLException("Ошибка записи элементов: " + e.getMessage(), e);
        }
    }

    // ========== ЭТАП 5: ОБНОВЛЕНИЕ function_id ==========

    private void updateFunctionIds() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                int updated = stmt.executeUpdate("""
                UPDATE code_elements 
                SET function_id = (
                    SELECT mf.id FROM metadata_functions mf 
                    WHERE mf.module_id = code_elements.module_id 
                      AND mf.function_name = code_elements.function_name 
                    LIMIT 1
                )
                WHERE function_id = -1
                """);
                System.out.println("  ✅ Обновлено function_id: " + updated);
            }
        }
    }

    // ========== ЭТАП 6: СВЯЗЫВАНИЕ owner_id ЧЕРЕЗ SQL (без Java-цикла) ==========

    private void updateOwnerIdsMultithreaded() throws SQLException {
        System.out.println("🚀 Связывание owner_id...");
        long startTime = System.currentTimeMillis();

        String sql = """
        UPDATE code_elements 
        SET owner_id = (
            SELECT ce2.id 
            FROM code_elements ce2 
            WHERE ce2.function_id = code_elements.function_id 
              AND ce2.element_name = code_elements.owner_name 
              AND ce2.element_type != 'ВызовФункции'
            LIMIT 1
        )
        WHERE owner_id IS NULL 
          AND owner_name IS NOT NULL 
          AND owner_name != ''
        """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                int updated = stmt.executeUpdate(sql);
                System.out.println("  ✅ Обновлено owner_id: " + updated);
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("  ✅ Готово за " + (endTime - startTime) + "ms");
    }


    private CodeElementData createCodeElementData(FunctionData func, CodeElement element) {
        return new CodeElementData(
                func.moduleId,
                -1,
                func.functionName,
                element.subtype,
                element.type,
                element.ownerName,
                element.ownerType,
                null,
                element.startLine,
                element.endLine > 0 ? element.endLine : element.startLine,
                element.text
        );
    }

    private List<FunctionData> parseModuleFunctions(int moduleId) {
        List<FunctionData> functions = new ArrayList<>();

        try {
            String modulePath = getModulePathFromDB(moduleId);
            if (modulePath == null || modulePath.trim().isEmpty()) return functions;

            File moduleFile = new File(modulePath);
            if (!moduleFile.exists() || moduleFile.length() == 0) return functions;

            List<ModuleParser.FunctionInfo> parsedFunctions =
                    ModuleParser.parseModuleFunctions(moduleFile);

            if (parsedFunctions.isEmpty()) return functions;

            List<String> lines = readFileLines(moduleFile);

            for (ModuleParser.FunctionInfo parsedFunc : parsedFunctions) {
                try {
                    String fullText = extractFullFunctionText(lines,
                            parsedFunc.startLine - 1, parsedFunc.endLine - 1);
                    functions.add(new FunctionData(
                            moduleId,
                            parsedFunc.name,
                            parsedFunc.type.equals("Процедура") ? "PROCEDURE" : "FUNCTION",
                            fullText,
                            parsedFunc.startLine,
                            parsedFunc.endLine
                    ));
                } catch (Exception e) {
                    System.err.println("⚠️ Ошибка обработки функции "
                            + parsedFunc.name + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка parseModuleFunctions для модуля " + moduleId
                    + ": " + e.getMessage());
        }

        return functions;
    }

    // Получаем путь из БД. Для этого метода используем отдельное соединение,
    // т.к. он вызывается из параллельных потоков.
    private String getModulePathFromDB(int moduleId) {
        String sql = "SELECT file_path FROM metadata_modules WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("file_path") : null;
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка получения пути для модуля " + moduleId + ": " + e.getMessage());
            return null;
        }
    }

    private List<String> readFileLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    firstLine = false;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    private String extractFullFunctionText(List<String> lines, int startLine, int endLine) {
        int commentStart = startLine;
        for (int i = startLine - 1; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) break;
            if (line.startsWith("//") || line.startsWith("#") || line.startsWith("/*")) {
                commentStart = i;
            } else {
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = commentStart; i <= endLine && i < lines.size(); i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString().trim();
    }

    private void cleanupDatabase() {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM metadata_functions");
                stmt.execute("DELETE FROM code_elements");
                conn.commit();
                stmt.execute("VACUUM");
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
                System.err.println("❌ Ошибка очистки БД: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.err.println("❌ Не удалось подключиться для очистки: " + e.getMessage());
        }
    }

    private void shutdownExecutors() {
        if (moduleExecutor != null) {
            moduleExecutor.shutdown();
            try {
                if (!moduleExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    moduleExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                moduleExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            moduleExecutor = null;
        }
    }

    public void cleanup() {
        shutdownExecutors();
        errors.clear();
        processedModules.set(0);
        failedModules.set(0);
        totalModules = 0;
        shutdownRequested = false;
        closeThreadConnection();
        System.gc();
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ==========

    private static class FunctionData {
        int    moduleId;
        String functionName;
        String functionType;
        String functionText;
        int    startLine;
        int    endLine;

        FunctionData(int moduleId, String functionName, String functionType,
                     String functionText, int startLine, int endLine) {
            this.moduleId     = moduleId;
            this.functionName = functionName;
            this.functionType = functionType;
            this.functionText = functionText;
            this.startLine    = startLine;
            this.endLine      = endLine;
        }
    }

    private static class CodeElementData {
        int     moduleId;
        int     functionId;
        String  functionName;
        String  elementName;
        String  elementType;
        String  ownerName;
        String  ownerType;
        Integer ownerId;
        int     startLine;
        int     endLine;
        String  elementText;

        CodeElementData(int moduleId, int functionId, String functionName, String elementName,
                        String elementType, String ownerName, String ownerType, Integer ownerId,
                        int startLine, int endLine, String elementText) {
            this.moduleId     = moduleId;
            this.functionId   = functionId;
            this.functionName = functionName;
            this.elementName  = elementName;
            this.elementType  = elementType;
            this.ownerName    = ownerName;
            this.ownerType    = ownerType;
            this.ownerId      = ownerId;
            this.startLine    = startLine;
            this.endLine      = endLine;
            this.elementText  = elementText;
        }
    }
}