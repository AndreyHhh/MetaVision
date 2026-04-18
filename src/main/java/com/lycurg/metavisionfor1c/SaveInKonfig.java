package com.lycurg.metavisionfor1c;

import javafx.scene.control.TreeItem;

import java.io.File;
import java.sql.*;



//# Сохранение дерева конфигурации из XML в SQLite БД с иконками объектов
public class SaveInKonfig {

    public static void save(TreeItem<String> root) throws SQLException {
        if (root == null) {
            throw new IllegalArgumentException("Корневой элемент не может быть null");
        }

        initializeDatabase();

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            conn.setAutoCommit(false);

            // Очищаем таблицу
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM Konfiguratsia");
            }

            // Сохраняем дерево
            String sql = "INSERT INTO Konfiguratsia (id, parent_id, name, icon_key) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int nextId = saveTree(ps, root, null, 1);
                ps.executeBatch();
            }

            conn.commit();

        } catch (Exception e) {
            throw new SQLException("Ошибка сохранения конфигурации: " + e.getMessage(), e);
        }
    }

    private static int saveTree(PreparedStatement ps, TreeItem<String> node, Integer parentId, int nextId) throws SQLException {
        int currentId = nextId++;

        // Сохраняем текущий узел
        ps.setInt(1, currentId);
        if (parentId != null) {
            ps.setInt(2, parentId);
        } else {
            ps.setNull(2, Types.INTEGER);
        }
        ps.setString(3, node.getValue());
        ps.setString(4, determineIconKey(node, node.getParent()));
        ps.addBatch();

        // Сохраняем детей
        for (TreeItem<String> child : node.getChildren()) {
            nextId = saveTree(ps, child, currentId, nextId);
        }

        return nextId;
    }


    // создание таблицы Konfiguratsia при первом запуске
    private static synchronized void initializeDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {

           // new File("data").mkdirs();

            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS Konfiguratsia (
                id INTEGER PRIMARY KEY,
                parent_id INTEGER,
                name TEXT NOT NULL,
                icon_key TEXT NOT NULL,
                FOREIGN KEY (parent_id) REFERENCES Konfiguratsia (id)
            )
            """);
            stmt.execute("PRAGMA busy_timeout = 30000;"); //защитим от блокировок
        }
    }

    private static String determineIconKey(TreeItem<String> node, TreeItem<String> parent) {
        String name = node.getValue();

        // 1. Если есть родитель
        if (parent != null) {
            String parentName = parent.getValue();

            // ОСОБЫЙ СЛУЧАЙ: "Общие" - это контейнер, его дети (подгруппы) имеют свои иконки
            if (parentName.equals("Общие")) {
                // Дети "Общие" - это подгруппы (Общие модули, Группы команд и т.д.)
                // Определяем иконку по имени подгруппы
                return getIconForSubgroup(name);
            }

            // Для всех остальных групп: если родитель - группа, берем иконку родителя
            if (isGroupNode(parentName)) {
                // Рекурсивно определяем иконку родителя
                return determineIconKey(parent, parent.getParent());
            }
        }

        // 2. Если родителя нет - определяем иконку по имени
        return getIconForGroup(name);
    }

    // Проверяем, является ли узел группой
    private static boolean isGroupNode(String nodeName) {
        String[] groups = {
                "Общие", "Константы", "Справочники", "Документы", "Журналы документов",
                "Регистры сведений", "Регистры накопления", "Планы счетов",
                "Планы видов характеристик", "Регистры бухгалтерии", "Обработки",
                "Отчёты", "Перечисления", "Общие модули", "Параметры сеанса", "Роли",
                "Общие реквизиты", "Планы обмена", "Подписки на события", "Критерии отбора",
                "Регламентные задания", "Функциональные опции", "Определяемые типы",
                "Хранилища настроек", "Общие команды", "Группы команд", "Общие формы",
                "Общие макеты", "Общие картинки", "XDTO пакеты", "Веб-сервисы",
                "HTTP-сервисы", "WS-ссылки", "Элементы стиля", "Стили", "Языки",
                "Подсистемы", "Параметры функциональных опций", "Бизнес-процессы", "Задачи",
                "Регистры расчетов", "Планы видов расчета"
        };

        for (String group : groups) {
            if (nodeName.equals(group)) {
                return true;
            }
        }
        return false;
    }

    // Иконки для подгрупп внутри "Общие"
    private static String getIconForSubgroup(String subgroupName) {
        if (subgroupName.equals("Общие модули")) return "obs_modyl";
        if (subgroupName.equals("Параметры сеанса")) return "modyl";
        if (subgroupName.equals("Роли")) return "roli";
        if (subgroupName.equals("Общие реквизиты")) return "obs_rekv";
        if (subgroupName.equals("Планы обмена")) return "plan_obmena";
        if (subgroupName.equals("Подписки на события")) return "podpiski";
        if (subgroupName.equals("Критерии отбора")) return "krit_otbora";
        if (subgroupName.equals("Регламентные задания")) return "reg_z";
        if (subgroupName.equals("Функциональные опции")) return "f_optsii";
        if (subgroupName.equals("Определяемые типы")) return "opred_t";
        if (subgroupName.equals("Хранилища настроек")) return "hran_nastr";
        if (subgroupName.equals("Общие команды")) return "obs_kom";
        if (subgroupName.equals("Группы команд")) return "gr_komand";
        if (subgroupName.equals("Общие формы")) return "obs_formy";
        if (subgroupName.equals("Общие макеты")) return "obs_makety";
        if (subgroupName.equals("Общие картинки")) return "obs_kart";
        if (subgroupName.equals("XDTO пакеты")) return "xdto_p";
        if (subgroupName.equals("Веб-сервисы")) return "web_s";
        if (subgroupName.equals("HTTP-сервисы")) return "http_s";
        if (subgroupName.equals("WS-ссылки")) return "ws_s";
        if (subgroupName.equals("Элементы стиля")) return "el_stylia";
        if (subgroupName.equals("Стили")) return "styli";
        if (subgroupName.equals("Языки")) return "yaz";
        if (subgroupName.equals("Подсистемы")) return "podsist";
        if (subgroupName.equals("Параметры функциональных опций")) return "f_optsii_param";

        return "conf";
    }

    // Иконки для основных групп
    private static String getIconForGroup(String groupName) {
        if (groupName.equals("Общие")) return "obs";
        if (groupName.equals("Константы")) return "konst";
        if (groupName.equals("Справочники")) return "sprav";
        if (groupName.equals("Документы")) return "dok";
        if (groupName.equals("Журналы документов")) return "zyrnal";
        if (groupName.equals("Регистры сведений")) return "r_sv";
        if (groupName.equals("Регистры накопления")) return "r_nak";
        if (groupName.equals("Планы счетов")) return "plan";
        if (groupName.equals("Планы видов характеристик")) return "plan_vh";
        if (groupName.equals("Регистры бухгалтерии")) return "r_byh";
        if (groupName.equals("Обработки")) return "obrabotka";
        if (groupName.equals("Отчёты")) return "ot4et";
        if (groupName.equals("Перечисления")) return "pere4isl";
        if (groupName.equals("Бизнес-процессы")) return "bizn_pr";
        if (groupName.equals("Задачи")) return "zada4a";
        if (groupName.equals("Регистры расчетов"))  return "r_ras4";
        if (groupName.equals("Планы видов расчета")) return "plan_vr";

        // Если это подгруппа (но без родителя), возвращаем иконку подгруппы
        return getIconForSubgroup(groupName);
    }

}