package com.lycurg.metavisionfor1c;

import java.io.File;
import java.sql.*;

//# Менеджер БД: создание таблиц, индексы, оптимизация
public class MetadataDbManager {

    private static final String DB_PATH = DBPathHelper.getDbPath();

    public static void initializeMetadataTables() throws SQLException {
        System.out.println("🗄️ DB URL: " + DBPathHelper.getDbUrl());
        System.out.println("🗄️ DB Path absolute: " + new File(DBPathHelper.getDbPath()).getAbsolutePath());
        System.out.println("🗄️ DB File exists: " + new File(DBPathHelper.getDbPath()).exists());

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA synchronous = NORMAL");
            stmt.execute("PRAGMA busy_timeout = 30000");
            stmt.execute("PRAGMA cache_size = -256000");
            stmt.execute("PRAGMA temp_store = MEMORY");
            stmt.execute("PRAGMA auto_vacuum = INCREMENTAL");

            stmt.execute("CREATE TABLE IF NOT EXISTS Konfiguratsia (" +
                    "id INTEGER PRIMARY KEY, " +
                    "parent_id INTEGER, " +
                    "name TEXT NOT NULL, " +
                    "icon_key TEXT NOT NULL, " +
                    "FOREIGN KEY (parent_id) REFERENCES Konfiguratsia (id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS metadata_objects (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "object_type TEXT NOT NULL, " +
                    "object_name TEXT NOT NULL, " +
                    "full_name TEXT NOT NULL UNIQUE, " +
                    "config_version TEXT, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS metadata_modules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "object_id INTEGER NOT NULL, " +
                    "object_full_name TEXT NOT NULL, " +
                    "module_type TEXT NOT NULL, " +
                    "module_name TEXT, " +
                    "file_path TEXT NOT NULL, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (object_id) REFERENCES metadata_objects (id), " +
                    "UNIQUE(object_id, module_type, module_name))");

            stmt.execute("CREATE TABLE IF NOT EXISTS metadata_functions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "module_id INTEGER NOT NULL, " +
                    "function_type TEXT NOT NULL, " +
                    "function_name TEXT NOT NULL, " +
                    "function_text TEXT NOT NULL, " +
                    "function_text_find TEXT, " +
                    "start_line INTEGER, " +
                    "end_line INTEGER, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (module_id) REFERENCES metadata_modules (id))");

            stmt.execute("CREATE TABLE IF NOT EXISTS code_elements (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "module_id INTEGER NOT NULL, " +
                    "function_id INTEGER NOT NULL, " +
                    "function_name TEXT NOT NULL, " +
                    "element_name TEXT NOT NULL, " +
                    "element_type TEXT NOT NULL, " +
                    "owner_name TEXT, " +
                    "owner_type TEXT, " +
                    "owner_id INTEGER, " +
                    "start_line INTEGER NOT NULL, " +
                    "end_line INTEGER, " +
                    "element_text TEXT, " +
                    "called_function_id INTEGER, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (module_id) REFERENCES metadata_modules (id), " +
                    "FOREIGN KEY (function_id) REFERENCES metadata_functions (id))");

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS recursive_functions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    function_id INTEGER NOT NULL UNIQUE,
                    function_name TEXT NOT NULL,
                    module_id INTEGER NOT NULL,
                    object_full_name TEXT NOT NULL,
                    recursion_type TEXT NOT NULL CHECK(recursion_type IN ('DIRECT', 'INDIRECT')),
                    recursion_chain TEXT,
                    detected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (function_id) REFERENCES metadata_functions (id),
                    FOREIGN KEY (module_id) REFERENCES metadata_modules (id)
                )
            """);

            System.out.println("✅ Все таблицы созданы/проверены");
        }
    }

    public static void disableIndexes() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {

            System.out.println("🔧 Отключаем индексы для ускорения загрузки...");

            String[] indexesToDisable = {
                    "idx_objects_name", "idx_objects_type", "idx_objects_full_name",
                    "idx_modules_object_id", "idx_modules_object_full_name", "idx_modules_type",
                    "idx_functions_module_id", "idx_functions_name", "idx_functions_type",
                    "idx_code_elements_module", "idx_code_elements_function", "idx_code_elements_type",
                    "idx_code_elements_subtype", "idx_code_elements_owner", "idx_code_elements_line",
            };

            int disabledCount = 0;
            for (String indexName : indexesToDisable) {
                try {
                    stmt.execute("DROP INDEX IF EXISTS " + indexName);
                    disabledCount++;
                } catch (SQLException e) {
                    System.err.println("⚠️ Не удалось удалить индекс: " + indexName);
                }
            }

            System.out.println("✅ Отключено индексов: " + disabledCount);
        }
    }

    public static void enableIndexes() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {


            System.out.println("🔧 Создаем индексы...");

            // metadata_functions
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_functions_id ON metadata_functions(id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_functions_module_id ON metadata_functions(module_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_functions_name ON metadata_functions(function_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_functions_text_find ON metadata_functions(function_text_find)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_functions_module_name ON metadata_functions(module_id, function_name)");

            // metadata_modules
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_modules_object_id ON metadata_modules(object_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_modules_object_full_name ON metadata_modules(object_full_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_modules_full_name_type ON metadata_modules(object_full_name, module_type)");

            // code_elements
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_module_func ON code_elements(module_id, function_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_function_id ON code_elements(function_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_type ON code_elements(element_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_owner_name ON code_elements(owner_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_owner_type ON code_elements(owner_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_owner_id ON code_elements(owner_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_called_function ON code_elements(called_function_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_type_called ON code_elements(element_type, called_function_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_calls_from_func ON code_elements(function_id, called_function_id) WHERE element_type = 'ВызовФункции'");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_owner_null ON code_elements(owner_name) WHERE owner_id IS NULL AND owner_name IS NOT NULL");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_type_owner ON code_elements(element_type, owner_id)");

            //для быстрого связывания owner_id
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_elements_func_owner ON code_elements(function_id, owner_name) WHERE owner_id IS NULL");

            System.out.println("✅ Все индексы созданы");
        }
    }

    public static void reindexAfterLinking() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {

            System.out.println("🔧 Переиндексация связанных вызовов и рекурсий...");

            // Индексы для called_function_id
            stmt.execute("DROP INDEX IF EXISTS idx_called_functions_linked");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_called_functions_linked ON code_elements(called_function_id) WHERE called_function_id IS NOT NULL");

            stmt.execute("DROP INDEX IF EXISTS idx_calls_from_to");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_calls_from_to ON code_elements(function_id, called_function_id) WHERE called_function_id IS NOT NULL");

            // Индексы для recursive_functions
            stmt.execute("DROP INDEX IF EXISTS idx_recursive_function_id");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_recursive_function_id ON recursive_functions(function_id)");

            stmt.execute("DROP INDEX IF EXISTS idx_recursive_function_name");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_recursive_function_name ON recursive_functions(function_name)");

            stmt.execute("DROP INDEX IF EXISTS idx_recursive_type");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_recursive_type ON recursive_functions(recursion_type)");

            stmt.execute("DROP INDEX IF EXISTS idx_recursive_module");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_recursive_module ON recursive_functions(module_id)");

            System.out.println("✅ Индексы для связей и рекурсий созданы");
        }
    }



    public static void optimizeDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {
            System.out.println("🔧 Оптимизация базы данных...");
            stmt.execute("PRAGMA optimize");
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            stmt.execute("ANALYZE");
            stmt.execute("PRAGMA incremental_vacuum");
            System.out.println("✅ База данных оптимизирована");
        }
    }

    public static void vacuumDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM");
            System.out.println("✅ База данных перестроена (VACUUM)");
        }
    }

    // ============================================================
    // saveObject — используется из UnifiedDataLoader батчем
    // ============================================================

    public static int saveObject(String objectType, String objectName,
                                 String fullName, String configVersion) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            return saveObject(conn, objectType, objectName, fullName, configVersion);
        }
    }

    // Перегрузка с готовым соединением — для батчевой загрузки
    public static int saveObject(Connection conn, String objectType, String objectName,
                                 String fullName, String configVersion) throws SQLException {
        String sql = """
            INSERT INTO metadata_objects
            (object_type, object_name, full_name, config_version)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(full_name) DO UPDATE SET
                object_type    = excluded.object_type,
                object_name    = excluded.object_name,
                config_version = excluded.config_version,
                created_at     = CURRENT_TIMESTAMP
            RETURNING id
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, objectType);
            ps.setString(2, objectName);
            ps.setString(3, fullName);
            ps.setString(4, configVersion);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
            throw new SQLException("Не удалось получить ID после сохранения объекта: " + fullName);
        }
    }

    // ============================================================
    // saveModule — две версии: с соединением и без
    // ============================================================

    public static int saveModule(int objectId, String objectFullName, String moduleType,
                                 String moduleName, String filePath) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            return saveModule(conn, objectId, objectFullName, moduleType, moduleName, filePath);
        }
    }

    // Перегрузка с готовым соединением — основной метод для батчевой загрузки
    public static int saveModule(Connection conn, int objectId, String objectFullName,
                                 String moduleType, String moduleName,
                                 String filePath) throws SQLException {
        String sql = """
            INSERT INTO metadata_modules
            (object_id, object_full_name, module_type, module_name, file_path)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(object_id, module_type, module_name) DO UPDATE SET
                file_path         = excluded.file_path,
                object_full_name  = excluded.object_full_name,
                created_at        = CURRENT_TIMESTAMP
            RETURNING id
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            ps.setString(2, objectFullName);
            ps.setString(3, moduleType);

            if (moduleName != null) {
                ps.setString(4, moduleName);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }

            ps.setString(5, filePath);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
            throw new SQLException("Не удалось получить ID после сохранения модуля");
        }
    }
}