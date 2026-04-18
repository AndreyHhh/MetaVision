package com.lycurg.metavisionfor1c;

import java.io.File;

public class DBPathHelper {

    private static String dbPath = null;

    /**
     * Определяет операционную систему
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static boolean isLinux() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux") || os.contains("aix");
    }



    public static String getDbPath() {
        if (dbPath != null) {
            return dbPath;
        }

        // На всех ОС — папка data/ рядом с программой
        String path = System.getProperty("user.dir") + File.separator + "data" + File.separator + "metavision.db";
        System.out.println("🗄️ DB path: " + path);

        File dbFile = new File(path);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (created) {
                System.out.println("📁 Создана папка: " + parentDir.getAbsolutePath());
            } else {
                System.err.println("❌ Не удалось создать папку: " + parentDir.getAbsolutePath());
            }
        }

        dbPath = path;
        return dbPath;
    }

    public static String getDbUrl() {
        String absolutePath = new File(getDbPath()).getAbsolutePath().replace("\\", "/");
        return "jdbc:sqlite:" + absolutePath;
    }



    /**
     * Для отладки
     */
    public static void main(String[] args) {
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println("isWindows: " + isWindows());
        System.out.println("isLinux: " + isLinux());
        System.out.println("DB Path: " + getDbPath());
        System.out.println("DB URL: " + getDbUrl());
    }
}