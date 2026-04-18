package com.lycurg.metavisionfor1c;

import javafx.scene.paint.Color;
import java.util.Map;
import java.util.HashMap;


//# Цветовая схема для типов элементов кода (маппинг типов БД → визуальные цвета)
public class ElementColors {

    // ЦВЕТА ДЛЯ ТИПОВ ЭЛЕМЕНТОВ
    private static final Map<String, Color> TYPE_COLORS = Map.of(
            "ОсновнаяФункция", Color.web("#FF6B6B"), // 🔴
            "ВызовФункции",    Color.web("#E3F2FD"), // 🔵
            "Цикл",            Color.web("#45B7D1"), // 🔵 синий (для обоих типов циклов)
            "Запрос",          Color.web("#FFEAA7"), // 🟡
            "Транзакция",      Color.web("#8B4513"), // 🟤
            "Блокировка",      Color.web("#A9A9A9")  // ⚫
    );

    // МАППИНГ: элемент БД → отображаемый тип
    private static final Map<String, String> TYPE_MAPPING = Map.of(
            "ЦиклНезависимый", "Цикл",
            "ЦиклЗапроса",     "Цикл",
            // Остальные остаются как есть
            "ВызовФункции",    "ВызовФункции",
            "Запрос",          "Запрос",
            "Транзакция",      "Транзакция",
            "Блокировка",      "Блокировка"
    );

    // ПОРЯДОК ОТОБРАЖЕНИЯ (приоритет)
    private static final String[] DISPLAY_ORDER = {
            "Блокировка", "Транзакция", "ВызовФункции", "Запрос", "Цикл"
    };

    public static Color getColorForType(String elementType) {
        // Преобразуем тип из БД в отображаемый
        String displayType = TYPE_MAPPING.getOrDefault(elementType, elementType);
        return TYPE_COLORS.getOrDefault(displayType, Color.GRAY);
    }

    public static String getDisplayType(String dbType) {
        return TYPE_MAPPING.getOrDefault(dbType, dbType);
    }

    // Метод для подсчета сгруппированных элементов
    public static Map<String, Integer> groupElementCounts(Map<String, Integer> dbCounts) {
        Map<String, Integer> grouped = new HashMap<>();

        for (Map.Entry<String, Integer> entry : dbCounts.entrySet()) {
            String dbType = entry.getKey();
            int count = entry.getValue();

            String displayType = getDisplayType(dbType);
            grouped.put(displayType, grouped.getOrDefault(displayType, 0) + count);
        }

        return grouped;
    }
}