package com.lycurg.metavisionfor1c;

import java.sql.*;
import java.util.*;

//# Полнотекстовый поиск по коду функций 1С с подсветкой найденных фрагментов
public class FunctionTextSearcher_poiskovik {


    //поиск текста в функциях (без учета регистра) с возвратом позиций для подсветки
    public static List<FunctionTextResult> searchInFunctions(String searchText) {
        List<FunctionTextResult> results = new ArrayList<>();

        if (searchText == null || searchText.trim().isEmpty()) {
            return results;
        }

        String sql = "SELECT mf.id, mf.function_name, mf.function_type, mf.function_text as full_text, " +
                "mm.object_full_name, mm.module_type, mm.file_path " +
                "FROM metadata_functions mf " +
                "JOIN metadata_modules mm ON mf.module_id = mm.id " +
                "WHERE mf.function_text_find LIKE ? " +
                "ORDER BY mm.object_full_name, mf.function_name";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String likePattern = "%" + searchText.toLowerCase() + "%";
            ps.setString(1, likePattern);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                FunctionTextResult result = new FunctionTextResult();
                result.id = rs.getInt("id");
                result.functionName = rs.getString("function_name");
                result.functionType = rs.getString("function_type");
                result.fullText = rs.getString("full_text");
                result.objectFullName = rs.getString("object_full_name");
                result.moduleType = rs.getString("module_type");
                result.filePath = rs.getString("file_path");

                // 🔥 НАХОДИМ ПОЗИЦИИ ДЛЯ ПОДСВЕТКИ
                String searchLower = searchText.toLowerCase();
                String functionTextLower = result.fullText.toLowerCase();
                List<int[]> positions = new ArrayList<>();

                int index = functionTextLower.indexOf(searchLower);
                while (index >= 0) {
                    positions.add(new int[]{index, index + searchLower.length()});
                    index = functionTextLower.indexOf(searchLower, index + 1);
                }
                result.highlightPositions = positions;

                results.add(result);
            }

            System.out.println("🔍 Найдено " + results.size() + " результатов для '" + searchText + "'");

        } catch (SQLException e) {
            System.err.println("❌ Ошибка поиска: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }


    //модель результата с массивами позиций для подсветки
    public static class FunctionTextResult {
        public int id;
        public String functionName;
        public String functionType;
        public String fullText;
        public String objectFullName;
        public String moduleType;
        public String filePath;
        public List<int[]> highlightPositions = new ArrayList<>(); // [start, end]

        // Геттеры для совместимости
        public List<int[]> getHighlightPositions() { return highlightPositions; }
        public String getFullText() { return fullText; }
    }

    // вывод статистики по текстам функций из БД
    public static void printTextsStatistics() {
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement()) {

            System.out.println("=== СТАТИСТИКА ТЕКСТОВ ФУНКЦИЙ (из metadata_functions) ===");

            ResultSet rs1 = stmt.executeQuery("SELECT COUNT(*) as total FROM metadata_functions");
            if (rs1.next()) {
                System.out.println("Всего функций с текстами: " + rs1.getInt("total"));
            }

            ResultSet rs2 = stmt.executeQuery(
                    "SELECT function_type, COUNT(*) as count FROM metadata_functions GROUP BY function_type");
            System.out.println("По типам:");
            while (rs2.next()) {
                System.out.println("  " + rs2.getString("function_type") + ": " + rs2.getInt("count"));
            }

            ResultSet rs3 = stmt.executeQuery(
                    "SELECT AVG(LENGTH(function_text)) as avg_length FROM metadata_functions");
            if (rs3.next()) {
                System.out.println("Средняя длина текста: " + rs3.getInt("avg_length") + " символов");
            }

        } catch (SQLException e) {
            System.err.println("❌ FunctionTextSearcher: Ошибка получения статистики: " + e.getMessage());
        }
    }

    // Тестовый метод для проверки поиска
    public static void main(String[] args) {
        if (args.length > 0) {
            String searchText = args[0];
            System.out.println("🔍 Поиск: '" + searchText + "'");

            // 🔥 ТЕСТИРУЕМ ОБА ВАРИАНТА
            System.out.println("=== БЕЗ УЧЕТА РЕГИСТРА ===");
            List<FunctionTextResult> resultsNoCase = searchInFunctions(searchText);
            System.out.println("📊 Найдено: " + resultsNoCase.size() + " результатов");

            System.out.println("=== С УЧЕТОМ РЕГИСТРА ===");
            List<FunctionTextResult> resultsCase = searchInFunctions(searchText);
            System.out.println("📊 Найдено: " + resultsCase.size() + " результатов");

            // Показываем первые 3 результата
            for (int i = 0; i < Math.min(3, resultsNoCase.size()); i++) {
                FunctionTextResult result = resultsNoCase.get(i);
                System.out.println((i + 1) + ". " + result.objectFullName +
                        " -> " + result.functionType + " " + result.functionName);
            }

        } else {
            System.out.println("Укажите текст для поиска: java FunctionTextSearcher_poiskovik \"текст для поиска\"");
            printTextsStatistics();
        }
    }

}