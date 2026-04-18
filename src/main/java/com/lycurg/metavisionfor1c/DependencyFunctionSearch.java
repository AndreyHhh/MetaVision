package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.fxmisc.richtext.CodeArea;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;


import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TablePosition;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
public class DependencyFunctionSearch {

    private final DependencyController mainController;
    private final ExecutorService executor;
    private final ObservableList<SearchResultWithIcon> searchResults = FXCollections.observableArrayList();
    private final IconManager iconManager = IconManager.getInstance();


    private final ConfigTreeManager configTreeManager = ConfigTreeManager.getInstance();


    private CodeSearchHelper searchTabCodeSearch;

    public static class SearchResultWithIcon {
        private final String iconKey;
        private final String objectName;
        private final String functionType;
        private final String functionName;
        private final String context;
        private final int functionId;

        public SearchResultWithIcon(String iconKey, String objectName, String functionType,
                                    String functionName, String context, int functionId) {
            this.iconKey = iconKey;
            this.objectName = objectName;
            this.functionType = functionType;
            this.functionName = functionName;
            this.context = context;
            this.functionId = functionId;
        }

        public String getIconKey() { return iconKey; }
        public String getObjectName() { return objectName; }
        public String getFunctionType() { return functionType; }
        public String getFunctionName() { return functionName; }
        public String getContext() { return context; }
        public int getFunctionId() { return functionId; }
    }

    public DependencyFunctionSearch(DependencyController controller) {
        this.mainController = controller;
        this.executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    }

    public void initialize() {
        setupSearchResultsTable();
        setupCodeSearch();
    }









    private void setupCodeSearch() {
        TextField searchField = mainController.getSearchTabCodeSearchField();
        Button prevBtn = mainController.getSearchTabCodeSearchPrevBtn();
        Button nextBtn = mainController.getSearchTabCodeSearchNextBtn();
        Label counter = mainController.getSearchTabCodeSearchCounter();
        CodeArea codeArea = mainController.getFunctionDetailCodeArea();

        if (searchField != null && codeArea != null) {
            searchTabCodeSearch = new CodeSearchHelper(codeArea, searchField, prevBtn, nextBtn, counter);
            System.out.println("✅ Поиск по модулю на вкладке 3 инициализирован");
        }
    }

    public void searchPrevInCode() {
        if (searchTabCodeSearch != null) {
            searchTabCodeSearch.navigatePrev();
        }
    }

    public void searchNextInCode() {
        if (searchTabCodeSearch != null) {
            searchTabCodeSearch.navigateNext();
        }
    }





    private void setupTableCopy(TableView<SearchResultWithIcon> table) {
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("📋 Копировать выделенное");
        MenuItem copyRowItem = new MenuItem("📋 Копировать строку");
        MenuItem copyCellItem = new MenuItem("📋 Копировать ячейку");
        SeparatorMenuItem separator = new SeparatorMenuItem();
        MenuItem rowModeItem = new MenuItem("✓ Режим: выделение строк");
        MenuItem cellModeItem = new MenuItem("☐ Режим: выделение ячеек");

        copyItem.setOnAction(e -> copySelectionToClipboard(table));
        copyRowItem.setOnAction(e -> copyCurrentRowToClipboard(table));
        copyCellItem.setOnAction(e -> copyCurrentCellToClipboard(table));
        rowModeItem.setOnAction(e -> setRowSelectionMode(table));
        cellModeItem.setOnAction(e -> setCellSelectionMode(table));

        contextMenu.getItems().addAll(copyItem, copyRowItem, copyCellItem, separator, rowModeItem, cellModeItem);
        table.setContextMenu(contextMenu);

        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copySelectionToClipboard(table);
                event.consume();
            }
            if (event.isControlDown() && event.getCode() == KeyCode.R) {
                if (table.getSelectionModel().isCellSelectionEnabled()) {
                    setRowSelectionMode(table);
                } else {
                    setCellSelectionMode(table);
                }
                event.consume();
            }
        });

        updateSelectionModeStatus(table);
    }

    private void setRowSelectionMode(TableView<SearchResultWithIcon> table) {
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateSelectionModeStatus(table);
    }

    private void setCellSelectionMode(TableView<SearchResultWithIcon> table) {
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateSelectionModeStatus(table);
    }

    private void updateSelectionModeStatus(TableView<SearchResultWithIcon> table) {
        // Статус не показываем, но копирование работает
        // Пользователь сам поймёт по контекстному меню
    }



    private void copySelectionToClipboard(TableView<SearchResultWithIcon> table) {
        if (table.getSelectionModel().isCellSelectionEnabled()) {
            copySelectedCellsToClipboard(table);
        } else {
            copySelectedRowsToClipboard(table);
        }
    }

    private void copySelectedRowsToClipboard(TableView<SearchResultWithIcon> table) {
        ObservableList<SearchResultWithIcon> selected = table.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("Объект\tТип\tФункция\tКонтекст\n");
        for (SearchResultWithIcon item : selected) {
            sb.append(item.getObjectName()).append("\t")
                    .append(item.getFunctionType()).append("\t")
                    .append(item.getFunctionName()).append("\t")
                    .append(item.getContext()).append("\n");
        }
        copyToClipboard(sb.toString());
    }

    private void copyCurrentRowToClipboard(TableView<SearchResultWithIcon> table) {
        SearchResultWithIcon selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String text = selected.getObjectName() + "\t" + selected.getFunctionType() + "\t" +
                selected.getFunctionName() + "\t" + selected.getContext();
        copyToClipboard(text);
    }

    private void copyCurrentCellToClipboard(TableView<SearchResultWithIcon> table) {
        TablePosition<?, ?> cell = table.getFocusModel().getFocusedCell();
        if (cell == null) return;

        Object value = table.getColumns().get(cell.getColumn()).getCellData(cell.getRow());
        if (value != null) {
            copyToClipboard(value.toString());
        }
    }

    private void copySelectedCellsToClipboard(TableView<SearchResultWithIcon> table) {
        ObservableList<TablePosition> selectedCells = table.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) return;

        Map<Integer, List<TablePosition>> rows = new TreeMap<>();
        for (TablePosition cell : selectedCells) {
            rows.computeIfAbsent(cell.getRow(), k -> new ArrayList<>()).add(cell);
        }

        StringBuilder sb = new StringBuilder();
        for (List<TablePosition> cellsInRow : rows.values()) {
            cellsInRow.sort(Comparator.comparingInt(TablePosition::getColumn));
            for (int i = 0; i < cellsInRow.size(); i++) {
                Object value = table.getColumns().get(cellsInRow.get(i).getColumn()).getCellData(cellsInRow.get(i).getRow());
                if (value != null) sb.append(value);
                if (i < cellsInRow.size() - 1) sb.append("\t");
            }
            sb.append("\n");
        }
        copyToClipboard(sb.toString());
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
    private void setupSearchResultsTable() {
        TableView<SearchResultWithIcon> newTable = createTableViewWithIcons();
        TableView<?> oldTable = mainController.getSearchResultsTable();

        oldTable.getColumns().clear();
        for (TableColumn<?, ?> col : newTable.getColumns()) {
            oldTable.getColumns().add((TableColumn) col);
        }

        @SuppressWarnings("unchecked")
        TableView<SearchResultWithIcon> typedOldTable = (TableView<SearchResultWithIcon>) oldTable;
        typedOldTable.setItems(searchResults);

        // 🔥 ДОБАВИТЬ ЭТУ СТРОКУ
        setupTableCopy(typedOldTable);

/*
        typedOldTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                showFunctionDetails(newSelection.getFunctionId());
            }
        });
*/


        typedOldTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                showFunctionDetails(newSelection.getFunctionId());
                // 🔥 ДОБАВИТЬ ЭТУ СТРОКУ
                Platform.runLater(() -> syncSearchTextToCodeSearch());
            }
        });
    }

    private TableView<SearchResultWithIcon> createTableViewWithIcons() {
        TableView<SearchResultWithIcon> tableView = new TableView<>();
        tableView.setItems(searchResults);

        TableColumn<SearchResultWithIcon, String> iconColumn = new TableColumn<>("");
        iconColumn.setPrefWidth(40);
        iconColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getIconKey()));
        iconColumn.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();

            @Override
            protected void updateItem(String iconKey, boolean empty) {
                super.updateItem(iconKey, empty);
                if (empty || iconKey == null) {
                    setGraphic(null);
                } else {
                    Image icon = iconManager.getIcon(iconKey);
                    imageView.setImage(icon);
                    imageView.setFitWidth(16);
                    imageView.setFitHeight(16);
                    setGraphic(imageView);
                }
            }
        });

        TableColumn<SearchResultWithIcon, String> objectColumn = new TableColumn<>("Объект");
        objectColumn.setPrefWidth(240);
        objectColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getObjectName()));

        TableColumn<SearchResultWithIcon, String> typeColumn = new TableColumn<>("Тип");
        typeColumn.setPrefWidth(100);
        typeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFunctionType()));

        TableColumn<SearchResultWithIcon, String> functionColumn = new TableColumn<>("Функция");
        functionColumn.setPrefWidth(250);
        functionColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFunctionName()));

        TableColumn<SearchResultWithIcon, String> contextColumn = new TableColumn<>("Контекст");
        contextColumn.setPrefWidth(800);
        contextColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getContext()));
        contextColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                if (empty || text == null) {
                    setText(null);
                } else {
                    setText(text);
                    setFont(Font.font("Consolas", 10));
                    setWrapText(true);
                    setPrefHeight(60);
                }
            }
        });

        tableView.getColumns().addAll(iconColumn, objectColumn, typeColumn, functionColumn, contextColumn);
        return tableView;
    }

    private String getIconKeyForObjectType(String objectType, String moduleType) {
        // Сначала пробуем через ConfigTreeManager по objectType
        String iconKey = configTreeManager.getIconKeyByDbObjectType(objectType);
        if (iconKey != null && !iconKey.equals("conf")) {
            return iconKey;
        }

        // Если не нашли, пробуем по moduleType
        if (moduleType != null) {
            switch (moduleType) {
                case "ОбщаяФорма": return "forma";
                case "ОбщийМодуль": return "obs_modyl";
                case "МодульМенеджера": return "modyl_menedzer";
                case "МодульОбъекта": return "modyl_osn";
                case "МодульФормы": return "forma";
                case "МодульКоманды": return "obs_kom";
                default: break;
            }
        }

        return "conf";
    }

    private String getRussianObjectName(String objectFullName, String moduleType) {
        if (objectFullName == null) return "";

        // Разделяем на префикс (тип) и имя
        int dotIndex = objectFullName.indexOf('.');
        String prefix = dotIndex > 0 ? objectFullName.substring(0, dotIndex) : objectFullName;
        String namePart = dotIndex > 0 ? objectFullName.substring(dotIndex) : "";

        // Пытаемся перевести префикс через ConfigTreeManager
        String russianPrefix = configTreeManager.getRussianNameByObjectType(prefix);

        // Если перевод найден и не равен исходному
        if (russianPrefix != null && !russianPrefix.equals(prefix)) {
            return russianPrefix + namePart;
        }

        // Если не перевелось, но это уже русское — возвращаем как есть
        if (objectFullName.matches(".*[а-яА-Я].*")) {
            return objectFullName;
        }

        // Fallback: пробуем через старый метод
        String russianName = configTreeManager.getRussianNameByObjectType(objectFullName);
        if (russianName != null && !russianName.equals(objectFullName)) {
            return russianName;
        }

        return objectFullName;
    }

    private void syncSearchTextToCodeSearch() {
        String searchText = mainController.getFunctionTextSearchField().getText();
        if (searchText == null || searchText.trim().isEmpty()) return;

        TextField codeSearchField = mainController.getSearchTabCodeSearchField();
        if (codeSearchField != null && searchTabCodeSearch != null) {
            codeSearchField.setText(searchText);
            // Используем существующий метод performSearch
            searchTabCodeSearch.performSearch(searchText);
        }
    }

    public void searchInFunctions() {
        String searchText = mainController.getFunctionTextSearchField().getText().trim();
        if (searchText.isEmpty()) {
            showAlert("⚠️ Ошибка", "Введите текст для поиска", Alert.AlertType.WARNING);
            return;
        }

        mainController.getSearchResultsCount().setText("Поиск...");
        mainController.getSearchTimeLabel().setText("");

        Task<List<SearchResultWithIcon>> searchTask = new Task<>() {
            @Override
            protected List<SearchResultWithIcon> call() throws Exception {
                Instant startTime = Instant.now();
                List<SearchResultWithIcon> results = performSearchWithIcons(searchText);
                Instant endTime = Instant.now();
                long durationMs = Duration.between(startTime, endTime).toMillis();

                Platform.runLater(() -> {
                    mainController.getSearchTimeLabel().setText("Время поиска: " + durationMs + " мс");
                });

                return results;
            }
        };

        searchTask.setOnSucceeded(e -> {
            List<SearchResultWithIcon> results = searchTask.getValue();
            searchResults.setAll(results);
            mainController.getSearchResultsCount().setText("Найдено: " + results.size() + " результатов");
        });

        searchTask.setOnFailed(e -> {
            searchResults.clear();
            mainController.getSearchResultsCount().setText("Ошибка поиска");
            showAlert("❌ Ошибка", "Ошибка выполнения поиска: " + searchTask.getException().getMessage(), Alert.AlertType.ERROR);
        });

        executor.submit(searchTask);
    }


    private List<SearchResultWithIcon> performSearchWithIcons(String searchText) throws SQLException {
        List<SearchResultWithIcon> results = new ArrayList<>();

        String sql = """
            SELECT 
                mf.function_name,
                mf.function_type,
                mf.function_text,
                mm.object_full_name,
                mm.module_type,
                mo.object_type,
                mf.id as function_id
            FROM metadata_functions mf
            JOIN metadata_modules mm ON mf.module_id = mm.id
            JOIN metadata_objects mo ON mm.object_id = mo.id
            WHERE mf.function_text LIKE ?
            ORDER BY mo.object_type, mm.object_full_name, mf.function_name
            LIMIT 10000
            """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + searchText + "%");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String functionName = rs.getString("function_name");
                String functionType = rs.getString("function_type");
                String fullText = rs.getString("function_text");
                String objectFullName = rs.getString("object_full_name");
                String moduleType = rs.getString("module_type");
                String objectType = rs.getString("object_type");
                int functionId = rs.getInt("function_id");

                String iconKey = getIconKeyForObjectType(objectType, moduleType);
                String context = getSimpleContext(fullText, searchText);

                String russianObjectName = getRussianObjectName(objectFullName, moduleType);
                String russianModuleType = getRussianModuleType(moduleType);
                SearchResultWithIcon result = new SearchResultWithIcon(
                        iconKey,
                        russianObjectName,
                        russianModuleType,  // ← теперь переведённый тип
                        functionName + " (" + functionType + ")",
                        context,
                        functionId
                );
                results.add(result);
            }
        }

        return results;
    }


    private String getRussianModuleType(String moduleType) {
        if (moduleType == null) return "";

        switch (moduleType) {
            case "Object": return "МодульОбъекта";
            case "Form": return "МодульФормы";
            case "Manager": return "МодульМенеджера";
            case "RecordSet": return "МодульНабораЗаписей";
            case "Module": return "Модуль";
            case "Command": return "Команда";
            case "ОбщаяФорма": return "ОбщаяФорма";
            case "ОбщийМодуль": return "ОбщийМодуль";
            case "МодульМенеджера": return "МодульМенеджера";
            case "МодульОбъекта": return "МодульОбъекта";
            case "МодульФормы": return "МодульФормы";
            default: return moduleType;
        }
    }



    private String getSimpleContext(String fullText, String searchText) {
        if (fullText == null || searchText == null) return "";

        int idx = fullText.toLowerCase().indexOf(searchText.toLowerCase());
        if (idx == -1) {
            idx = 0;
        }

        int start = Math.max(0, idx - 100);
        int end = Math.min(fullText.length(), idx + searchText.length() + 100);

        String snippet = fullText.substring(start, end);
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < fullText.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }

    private void showFunctionDetails(int functionId) {
        String sql = "SELECT function_text FROM metadata_functions WHERE id = ?";

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
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
                CodeArea codeArea = mainController.getFunctionDetailCodeArea();
                codeArea.replaceText(functionText);
                OneCHighlighter.apply1CColors(codeArea);

                // 🔥 ПОСЛЕ ЗАГРУЗКИ ТЕКСТА ЗАПУСКАЕМ ПОИСК
                String searchText = mainController.getFunctionTextSearchField().getText();
                if (searchText != null && !searchText.trim().isEmpty() && searchTabCodeSearch != null) {
                    TextField codeSearchField = mainController.getSearchTabCodeSearchField();
                    if (codeSearchField != null) {
                        codeSearchField.setText(searchText);
                        // Небольшая задержка для отрисовки
                        new Thread(() -> {
                            try { Thread.sleep(100); } catch (InterruptedException ex) {}
                            Platform.runLater(() -> searchTabCodeSearch.performSearch(searchText));
                        }).start();
                    }
                }
            }
        });

        executor.submit(task);
    }

    public void clearFunctionSearch() {
        mainController.getFunctionTextSearchField().clear();
        searchResults.clear();
        mainController.getSearchResultsCount().setText("Найдено: 0 результатов");
        mainController.getSearchTimeLabel().setText("");
        mainController.getFunctionDetailCodeArea().clear();
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

    public void shutdown() {
        executor.shutdown();
    }
}