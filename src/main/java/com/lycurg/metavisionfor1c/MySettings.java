package com.lycurg.metavisionfor1c;

import java.io.File;
import java.sql.*;

/**
 * Менеджер для работы с базой данных настроек SQLite
 * Хранит настройки в формате ключ-значение
 */
public class MySettings {

    private static final String TABLE_NAME = "settings";
    private Connection connection;
    private String dbPath;

    /**
     * Конструктор по умолчанию - создает БД в папке data/
     */
    public MySettings() {
        String workDir = System.getProperty("user.dir");
        File dataDir = new File(workDir, "data");

        if (!dataDir.exists()) {
            dataDir.mkdirs();
            System.out.println("📁 Создана папка: " + dataDir.getAbsolutePath());
        }

        this.dbPath = new File(dataDir, "settings.db").getAbsolutePath();
        initDatabase();
    }

    /**
     * Инициализация базы данных и создание таблицы
     */
    private void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");

            String url = "jdbc:sqlite:" + dbPath;
            connection = DriverManager.getConnection(url);

            // Английские имена колонок - работает везде
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS %s (
                    setting_key TEXT PRIMARY KEY NOT NULL,
                    setting_value TEXT
                )
                """.formatted(TABLE_NAME);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(createTableSQL);
                System.out.println("✅ MySettings: " + dbPath);
            }

        } catch (ClassNotFoundException e) {
            System.err.println("❌ SQLite driver not found");
        } catch (SQLException e) {
            System.err.println("❌ MySettings init error: " + e.getMessage());
        }
    }

    /**
     * Сохранить или обновить значение по ключу
     */
    public void set(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (setting_key, setting_value) VALUES (?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Failed to save: " + key);
        }
    }

    /**
     * Получить значение по ключу
     */
    public String get(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT setting_value FROM " + TABLE_NAME + " WHERE setting_key = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("setting_value");
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to read: " + key);
        }

        return null;
    }

    /**
     * Получить значение по ключу с дефолтным значением
     */
    public String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Удалить запись по ключу
     */
    public boolean delete(String key) {
        if (key == null || key.trim().isEmpty()) {
            return false;
        }

        String sql = "DELETE FROM " + TABLE_NAME + " WHERE setting_key = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, key);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("❌ Failed to delete: " + key);
            return false;
        }
    }

    /**
     * Проверить существование ключа
     */
    public boolean exists(String key) {
        return get(key) != null;
    }

    /**
     * Получить все настройки в виде строки (для отладки)
     */
    public String getAllSettings() {
        StringBuilder sb = new StringBuilder();
        String sql = "SELECT setting_key, setting_value FROM " + TABLE_NAME + " ORDER BY setting_key";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                sb.append(rs.getString("setting_key"))
                        .append(" = ")
                        .append(rs.getString("setting_value"))
                        .append("\n");
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to read all settings");
        }

        return sb.toString();
    }

    /**
     * Закрыть соединение с БД
     */
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("✅ MySettings closed");
            } catch (SQLException e) {
                System.err.println("❌ Error closing MySettings: " + e.getMessage());
            }
        }
    }
}