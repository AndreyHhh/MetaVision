package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

//# Контроллер вкладки 6: сканер проблемного кода (длинные функции, магические числа, сложные условия)
public class DependencyProblematicCode {

    private final DependencyController mainController;
    private final ExecutorService executor;
    private final ObservableList<CodeProblem> problemIssues = FXCollections.observableArrayList();

    // 🔥 КЛАСС ДЛЯ ХРАНЕНИЯ ПРОБЛЕМ КОДА
    public static class CodeProblem {
        private final String level;
        private final String category;
        private final String functionName;
        private final String objectName;
        private final String problematicCode;
        private final int lineNumber;
        private final int functionId;
        private final String recommendation;

        public CodeProblem(String level, String category, String functionName,
                           String objectName, String problematicCode, int lineNumber,
                           int functionId, String recommendation) {
            this.level = level;
            this.category = category;
            this.functionName = functionName;
            this.objectName = objectName;
            this.problematicCode = problematicCode;
            this.lineNumber = lineNumber;
            this.functionId = functionId;
            this.recommendation = recommendation;
        }

        public String getLevel() { return level; }
        public String getCategory() { return category; }
        public String getFunctionName() { return functionName; }
        public String getObjectName() { return objectName; }
        public String getProblematicCode() { return problematicCode; }
        public int getLineNumber() { return lineNumber; }
        public int getFunctionId() { return functionId; }
        public String getRecommendation() { return recommendation; }
    }

    public DependencyProblematicCode(DependencyController controller) {
        this.mainController = controller;
        this.executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    }

    public void initialize() {
        setupProblemIssuesTable();
    }

    private void setupProblemIssuesTable() {
        TableView<CodeProblem> tableView = getProblemIssuesTable();
        if (tableView == null) return;

        // Настройка колонок
        TableColumn<CodeProblem, String> levelColumn = new TableColumn<>("Уровень");
        levelColumn.setPrefWidth(80);
        levelColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getLevel()));

        TableColumn<CodeProblem, String> categoryColumn = new TableColumn<>("Категория");
        categoryColumn.setPrefWidth(150);
        categoryColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getCategory()));

        TableColumn<CodeProblem, String> functionColumn = new TableColumn<>("Функция");
        functionColumn.setPrefWidth(200);
        functionColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFunctionName()));

        TableColumn<CodeProblem, String> objectColumn = new TableColumn<>("Объект");
        objectColumn.setPrefWidth(200);
        objectColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getObjectName()));

        TableColumn<CodeProblem, String> codeColumn = new TableColumn<>("Проблемный код");
        codeColumn.setPrefWidth(300);
        codeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getProblematicCode()));
        codeColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) {
                    setText(null);
                } else {
                    setText(text);
                    setWrapText(true);
                }
            }
        });

        TableColumn<CodeProblem, Integer> lineColumn = new TableColumn<>("Строка");
        lineColumn.setPrefWidth(60);
        lineColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getLineNumber()).asObject());

        tableView.getColumns().addAll(levelColumn, categoryColumn, functionColumn,
                objectColumn, codeColumn, lineColumn);
        tableView.setItems(problemIssues);

        // Обработчик выбора
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                showProblemDetails(newSelection);
            }
        });
    }

    //запуск сканирования функций на проблемы
    public void runProblematicCodeScan() {
        mainController.getProblemScanProgress().setVisible(true);
        mainController.getProblemTotalLabel().setText("Сканирование...");

        // Сбрасываем статистику
        updateProblemStats(0, 0, 0, 0, 0);

        Task<List<CodeProblem>> scanTask = new Task<>() {
            @Override
            protected List<CodeProblem> call() throws Exception {
                return performProblemScan();
            }
        };

        scanTask.setOnSucceeded(e -> {
            List<CodeProblem> results = scanTask.getValue();
            problemIssues.setAll(results);

            int total = results.size();
            int critical = (int) results.stream().filter(p -> "CRITICAL".equals(p.getLevel())).count();
            int high = (int) results.stream().filter(p -> "HIGH".equals(p.getLevel())).count();
            int medium = (int) results.stream().filter(p -> "MEDIUM".equals(p.getLevel())).count();
            int low = (int) results.stream().filter(p -> "LOW".equals(p.getLevel())).count();

            updateProblemStats(total, critical, high, medium, low);
            mainController.getProblemScanProgress().setVisible(false);
        });

        scanTask.setOnFailed(e -> {
            problemIssues.clear();
            updateProblemStats(0, 0, 0, 0, 0);
            mainController.getProblemScanProgress().setVisible(false);
            showAlert("❌ Ошибка", "Ошибка сканирования: " + scanTask.getException().getMessage(),
                    Alert.AlertType.ERROR);
        });

        executor.submit(scanTask);
    }

    //детектирует антипаттерны (длина, вложенность, дублирование)
    private List<CodeProblem> performProblemScan() throws SQLException {
        List<CodeProblem> problems = new ArrayList<>();

        // 🔥 ТУТ БУДЕТ ЛОГИКА СКАНИРОВАНИЯ
        // Пока пример - сканируем функции на наличие потенциальных проблем

        String sql = """
            SELECT 
                mf.id as function_id,
                mf.function_name,
                mf.function_type,
                mf.function_text,
                mm.object_full_name,
                mm.module_type,
                mo.object_type
            FROM metadata_functions mf
            JOIN metadata_modules mm ON mf.module_id = mm.id
            JOIN metadata_objects mo ON mm.object_id = mo.id
            WHERE LENGTH(mf.function_text) > 10
            ORDER BY mm.object_full_name, mf.function_name
            LIMIT 500
            """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String functionName = rs.getString("function_name");
                String functionText = rs.getString("function_text");
                String objectName = rs.getString("object_full_name");
                int functionId = rs.getInt("function_id");

                // 🔥 ПРИМЕРЫ ПРОВЕРОК (РАСШИРЬ ЭТОТ СПИСОК)

                // 1. Проверка на длинные функции (более 100 строк)
                String[] lines = functionText.split("\n");
                if (lines.length > 100) {
                    problems.add(new CodeProblem(
                            "MEDIUM", "Длинная функция", functionName, objectName,
                            "Функция содержит " + lines.length + " строк",
                            lines.length, functionId,
                            "Рекомендуется разбить функцию на более мелкие. Максимум 50-80 строк."
                    ));
                }

                // 2. Проверка на глубокую вложенность
                int maxNesting = checkMaxNesting(functionText);
                if (maxNesting > 4) {
                    problems.add(new CodeProblem(
                            "MEDIUM", "Глубокая вложенность", functionName, objectName,
                            "Максимальная вложенность: " + maxNesting + " уровней",
                            1, functionId,
                            "Упростите логику. Рекомендуется не более 3-4 уровней вложенности."
                    ));
                }

                // 3. Проверка на магические числа
                List<String> magicNumbers = findMagicNumbers(functionText);
                for (String magicNum : magicNumbers) {
                    problems.add(new CodeProblem(
                            "LOW", "Магическое число", functionName, objectName,
                            "Магическое число: " + magicNum,
                            findLineNumber(functionText, magicNum), functionId,
                            "Вынесите число в константу с осмысленным именем."
                    ));
                }

                // 4. Проверка на сложные условия
                if (hasComplexCondition(functionText)) {
                    problems.add(new CodeProblem(
                            "MEDIUM", "Сложное условие", functionName, objectName,
                            "Слишком сложное логическое условие",
                            1, functionId,
                            "Упростите условие или вынесите его в отдельную функцию."
                    ));
                }

                // 5. Проверка на повторяющийся код
                if (hasDuplicateCodePatterns(functionText)) {
                    problems.add(new CodeProblem(
                            "HIGH", "Повторяющийся код", functionName, objectName,
                            "Обнаружены повторяющиеся блоки кода",
                            1, functionId,
                            "Вынесите повторяющийся код в отдельную функцию или метод."
                    ));
                }
            }
        }

        return problems;
    }

    // 🔥 ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ АНАЛИЗА

    private int checkMaxNesting(String code) {
        int maxNesting = 0;
        int currentNesting = 0;

        String[] lines = code.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("Если") || line.startsWith("Для") ||
                    line.startsWith("Пока") || line.startsWith("Попытка")) {
                currentNesting++;
                maxNesting = Math.max(maxNesting, currentNesting);
            } else if (line.startsWith("КонецЕсли") || line.startsWith("КонецЦикла") ||
                    line.startsWith("КонецПопытки")) {
                currentNesting--;
            }
        }
        return maxNesting;
    }

    private List<String> findMagicNumbers(String code) {
        List<String> numbers = new ArrayList<>();
        // Ищем числа кроме 0, 1, 10, 100, 1000 (часто используемые)
        Pattern pattern = Pattern.compile("\\b([2-9]|[1-9][0-9][0-9]+)\\b");
        java.util.regex.Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String num = matcher.group();
            if (!numbers.contains(num)) {
                numbers.add(num);
            }
        }
        return numbers;
    }

    private boolean hasComplexCondition(String code) {
        // Проверяем на сложные условия с множеством И/ИЛИ
        String[] lines = code.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("Если ") || line.contains("Или ") || line.contains("И ")) {
                int andCount = countOccurrences(line, "И ");
                int orCount = countOccurrences(line, "Или ");
                if (andCount + orCount > 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasDuplicateCodePatterns(String code) {
        // Простая проверка на дублирование (можно улучшить)
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length - 3; i++) {
            String pattern = lines[i] + lines[i+1] + lines[i+2];
            for (int j = i + 3; j < lines.length - 3; j++) {
                String check = lines[j] + lines[j+1] + lines[j+2];
                if (pattern.equals(check) && pattern.length() > 20) {
                    return true;
                }
            }
        }
        return false;
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    private int findLineNumber(String code, String searchText) {
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(searchText)) {
                return i + 1;
            }
        }
        return 1;
    }


//отображение деталей выбранной проблемы
    private void showProblemDetails(CodeProblem problem) {
        // Обновляем метки
        mainController.getSelectedProblemLabel().setText(
                problem.getFunctionName() + " - " + problem.getCategory());
        mainController.getProblemLocationLabel().setText(
                "Объект: " + problem.getObjectName() + " | Строка: " + problem.getLineNumber());

        // Загружаем рекомендации
        mainController.getProblemDetailText().setText(problem.getRecommendation());

        // Загружаем текст функции
        loadFunctionTextForProblem(problem.getFunctionId(), problem.getFunctionName());
    }

    private void loadFunctionTextForProblem(int functionId, String functionName) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                String sql = "SELECT function_text FROM metadata_functions WHERE id = ?";
                try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, functionId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return rs.getString("function_text");
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            String functionText = task.getValue();
            if (functionText != null) {
                mainController.getProblemFunctionCodeArea().replaceText(functionText);
                OneCHighlighter.apply1CColors(mainController.getProblemFunctionCodeArea());
                mainController.getProblemFunctionInfoLabel().setText(
                        "Функция: " + functionName + " | Строк: " + functionText.split("\n").length);
            }
        });

        executor.submit(task);
    }


    //обновление статистики по уровням критичности
    private void updateProblemStats(int total, int critical, int high, int medium, int low) {
        Platform.runLater(() -> {
            mainController.getProblemTotalLabel().setText("Всего проблем: " + total);
            mainController.getProblemCriticalLabel().setText("CRITICAL: " + critical);
            mainController.getProblemHighLabel().setText("HIGH: " + high);
            mainController.getProblemMediumLabel().setText("MEDIUM: " + medium);
            mainController.getProblemLowLabel().setText("LOW: " + low);
        });
    }

    public void showProblemStats() {
        // TODO: Показать подробную статистику
        showAlert("📊 Статистика",
                "Всего проблем: " + problemIssues.size() + "\n" +
                        "CRITICAL: " + countProblemsByLevel("CRITICAL") + "\n" +
                        "HIGH: " + countProblemsByLevel("HIGH") + "\n" +
                        "MEDIUM: " + countProblemsByLevel("MEDIUM") + "\n" +
                        "LOW: " + countProblemsByLevel("LOW"),
                Alert.AlertType.INFORMATION);
    }

    public void exportProblemReport() {
        // TODO: Экспорт отчета
        showAlert("📋 Экспорт", "Функция экспорта отчета в разработке", Alert.AlertType.INFORMATION);
    }

    public void copyProblemFunctionText() {
        // TODO: Копирование текста функции
        showAlert("📋 Копирование", "Функция копирования в разработке", Alert.AlertType.INFORMATION);
    }

    private int countProblemsByLevel(String level) {
        return (int) problemIssues.stream().filter(p -> p.getLevel().equals(level)).count();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // 🔥 ГЕТТЕРЫ (нужно добавить эти поля в DependencyGraphController)
    private TableView<CodeProblem> getProblemIssuesTable() {
        return (TableView<CodeProblem>) mainController.getProblemIssuesTable();
    }

    public void shutdown() {
        executor.shutdown();
    }
}