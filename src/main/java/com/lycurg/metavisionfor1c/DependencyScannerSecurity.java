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
import javafx.scene.input.KeyCode;
import org.fxmisc.richtext.CodeArea;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class DependencyScannerSecurity {

    private final DependencyController mainController;
    private final IconManager iconManager = IconManager.getInstance();

    private String currentSearchText = "";
    private List<Scanner_Security.SecurityIssue> allSecurityIssues = new ArrayList<>();
    private CodeSearchHelper securityCodeSearch;

    public DependencyScannerSecurity(DependencyController controller) {
        this.mainController = controller;
    }

    // ========== ИНИЦИАЛИЗАЦИЯ ==========

    public void initialize() {
        setupSecurityTable();
        setupSecuritySyntaxHighlighting();
        initializeSecurityStats();
        setupExportButton();
        setupBackgroundJobsButton();
        setupStatusBar();
        setupSecuritySearch();
        setupCodeSearch();
    }

    private void setupCodeSearch() {
        TextField searchField = mainController.securityCodeSearchField;
        Button prevBtn = mainController.securityCodeSearchPrevBtn;
        Button nextBtn = mainController.securityCodeSearchNextBtn;
        Label counter = mainController.securityCodeSearchCounter;
        CodeArea codeArea = mainController.getSecurityFunctionCodeArea();

        if (searchField != null && codeArea != null) {
            securityCodeSearch = new CodeSearchHelper(codeArea, searchField, prevBtn, nextBtn, counter);
        }
    }

    public void searchPrevInCode() {
        if (securityCodeSearch != null) securityCodeSearch.navigatePrev();
    }

    public void searchNextInCode() {
        if (securityCodeSearch != null) securityCodeSearch.navigateNext();
    }

    private void setupSecuritySyntaxHighlighting() {
        CodeArea codeArea = mainController.getSecurityFunctionCodeArea();
        if (codeArea != null) {
            codeArea.textProperty().addListener((obs, oldVal, newVal) ->
                    OneCHighlighter.apply1CColors(codeArea));
            OneCHighlighter.apply1CColors(codeArea);
        }
    }

    private void initializeSecurityStats() {
        mainController.getSecurityTotalLabel().setText("Всего проблем: 0");
        mainController.getSecurityCriticalLabel().setText("CRITICAL: 0");
        mainController.getSecurityHighLabel().setText("HIGH: 0");
        mainController.getSecurityMediumLabel().setText("MEDIUM: 0");

        Button perfButton = mainController.getSecurityPerformanceButton();
        Button errorsButton = mainController.getSecurityErrorsButton();
        if (perfButton != null) perfButton.setVisible(false);
        if (errorsButton != null) errorsButton.setVisible(false);
    }

    private void setupExportButton() {
        Button exportButton = mainController.getSecurityExportButton();
        if (exportButton != null) exportButton.setOnAction(e -> exportSecurityReport());
    }

    private void setupBackgroundJobsButton() {
        Button backgroundJobsButton = mainController.getSecurityBackgroundJobsButton();
        if (backgroundJobsButton != null) backgroundJobsButton.setOnAction(e -> scanBackgroundJobs());
    }

    private void setupStatusBar() {
        Label statusLabel = mainController.getSecurityStatusLabel();
        if (statusLabel != null) statusLabel.setAlignment(javafx.geometry.Pos.CENTER);
    }

    // ========== ТАБЛИЦА ==========

    private void setupSecurityTable() {
        TableView<Scanner_Security.SecurityIssue> table = mainController.getSecurityIssuesTable();
        if (table == null) {
            System.err.println("❌ Таблица securityIssuesTable не найдена");
            return;
        }

        table.getColumns().clear();

        // Иконка
        TableColumn<Scanner_Security.SecurityIssue, String> iconCol = new TableColumn<>("");
        iconCol.setPrefWidth(40);
        iconCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        getIconKeyForObject(cell.getValue().objectFullName, cell.getValue().moduleType)));

        iconCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            @Override
            protected void updateItem(String iconKey, boolean empty) {
                super.updateItem(iconKey, empty);
                if (empty || iconKey == null) {
                    setGraphic(null);
                } else {
                    Image icon = iconManager.getIcon(iconKey);
                    if (icon != null) {
                        imageView.setImage(icon);
                        imageView.setFitWidth(16);
                        imageView.setFitHeight(16);
                        setGraphic(imageView);
                    }
                }
            }
        });

        // Уровень
        TableColumn<Scanner_Security.SecurityIssue, String> severityCol = new TableColumn<>("Уровень");
        severityCol.setPrefWidth(80);
        severityCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().severity));
        severityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                if (empty || severity == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(severity);
                    switch (severity) {
                        case "CRITICAL": setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;"); break;
                        case "HIGH":     setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;"); break;
                        default:         setStyle("-fx-text-fill: #ffc107;"); break;
                    }
                }
            }
        });

        // Тип
        TableColumn<Scanner_Security.SecurityIssue, String> typeCol = new TableColumn<>("Тип");
        typeCol.setPrefWidth(150);
        typeCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().type));

        // Функция
        TableColumn<Scanner_Security.SecurityIssue, String> functionCol = new TableColumn<>("Функция");
        functionCol.setPrefWidth(200);
        functionCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().functionName));

        // Объект
        TableColumn<Scanner_Security.SecurityIssue, String> objectCol = new TableColumn<>("Объект");
        objectCol.setPrefWidth(200);
        objectCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        configTreeManager.getRussianNameByObjectType(cellData.getValue().objectFullName)
                ));

        // Код
        TableColumn<Scanner_Security.SecurityIssue, String> codeCol = new TableColumn<>("Код");
        codeCol.setPrefWidth(300);
        codeCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().vulnerableCode));

        // Строка
        TableColumn<Scanner_Security.SecurityIssue, Integer> lineCol = new TableColumn<>("Строка");
        lineCol.setPrefWidth(60);
        lineCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleIntegerProperty(cell.getValue().lineNumber).asObject());

        table.getColumns().addAll(iconCol, severityCol, typeCol, functionCol, objectCol, codeCol, lineCol);

        // Клик по строке
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showSecurityFunctionDetails(newVal);
            } else {
                clearSecurityDetails();
            }
        });

        setupSecurityTableCopy();
    }

    // ========== КОПИРОВАНИЕ ==========

    private void setupSecurityTableCopy() {
        TableView<Scanner_Security.SecurityIssue> table = mainController.getSecurityIssuesTable();
        if (table == null) return;

        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem     = new MenuItem("📋 Копировать выделенное");
        MenuItem copyRowItem  = new MenuItem("📋 Копировать строку");
        MenuItem copyCellItem = new MenuItem("📋 Копировать ячейку");
        MenuItem rowModeItem  = new MenuItem("✓ Режим: выделение строк");
        MenuItem cellModeItem = new MenuItem("☐ Режим: выделение ячеек");

        copyItem.setOnAction(e     -> copySelectionToClipboard(table));
        copyRowItem.setOnAction(e  -> copyCurrentRowToClipboard(table));
        copyCellItem.setOnAction(e -> copyCurrentCellToClipboard(table));
        rowModeItem.setOnAction(e  -> setRowSelectionMode(table));
        cellModeItem.setOnAction(e -> setCellSelectionMode(table));

        contextMenu.getItems().addAll(copyItem, copyRowItem, copyCellItem,
                new SeparatorMenuItem(), rowModeItem, cellModeItem);
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

    private void setRowSelectionMode(TableView<Scanner_Security.SecurityIssue> table) {
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateSelectionModeStatus(table);
    }

    private void setCellSelectionMode(TableView<Scanner_Security.SecurityIssue> table) {
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        updateSelectionModeStatus(table);
    }

    private void updateSelectionModeStatus(TableView<Scanner_Security.SecurityIssue> table) {
        Platform.runLater(() -> {
            if (mainController.getVulnerabilityLocationLabel() != null) {
                String mode = table.getSelectionModel().isCellSelectionEnabled()
                        ? "🔲 режим: ячейки" : "📌 режим: строки";
                mainController.getVulnerabilityLocationLabel().setText(mode + " | Ctrl+R - переключить");
            }
        });
    }

    private void copySelectionToClipboard(TableView<Scanner_Security.SecurityIssue> table) {
        if (table.getSelectionModel().isCellSelectionEnabled()) {
            copySelectedCellsToClipboard(table);
        } else {
            copySelectedRowsToClipboard(table);
        }
    }

    private void copySelectedRowsToClipboard(TableView<Scanner_Security.SecurityIssue> table) {
        ObservableList<Scanner_Security.SecurityIssue> selected = table.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) { addMessage("⚠️ Нет выделенных строк"); return; }

        StringBuilder sb = new StringBuilder();
        sb.append("Уровень\tТип\tФункция\tОбъект\tКод\tСтрока\n");
        for (Scanner_Security.SecurityIssue issue : selected) {
            sb.append(issue.severity).append("\t")
                    .append(issue.type).append("\t")
                    .append(nvl(issue.functionName)).append("\t")
                    .append(nvl(issue.objectFullName)).append("\t")
                    .append(nvl(issue.vulnerableCode)).append("\t")
                    .append(issue.lineNumber).append("\n");
        }
        copyToClipboard(sb.toString());
        addMessage("📋 Скопировано " + selected.size() + " строк");
    }

    private void copyCurrentRowToClipboard(TableView<Scanner_Security.SecurityIssue> table) {
        Scanner_Security.SecurityIssue selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { addMessage("⚠️ Нет выделенной строки"); return; }

        String text = selected.severity + "\t" + selected.type + "\t" +
                nvl(selected.functionName) + "\t" + nvl(selected.objectFullName) + "\t" +
                nvl(selected.vulnerableCode) + "\t" + selected.lineNumber;
        copyToClipboard(text);
        addMessage("📋 Скопирована строка: " + selected.functionName);
    }

    private void copyCurrentCellToClipboard(TableView<Scanner_Security.SecurityIssue> table) {
        TablePosition<?, ?> cell = table.getFocusModel().getFocusedCell();
        if (cell == null) { addMessage("⚠️ Нет выделенной ячейки"); return; }

        Object value = table.getColumns().get(cell.getColumn()).getCellData(cell.getRow());
        if (value != null) {
            copyToClipboard(value.toString());
            addMessage("📋 Скопирована ячейка");
        } else {
            addMessage("⚠️ Ячейка пуста");
        }
    }

    private void copySelectedCellsToClipboard(TableView<Scanner_Security.SecurityIssue> table) {
        ObservableList<TablePosition> selectedCells = table.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) { addMessage("⚠️ Нет выделенных ячеек"); return; }

        Map<Integer, List<TablePosition>> rows = new TreeMap<>();
        for (TablePosition cell : selectedCells) {
            rows.computeIfAbsent(cell.getRow(), k -> new ArrayList<>()).add(cell);
        }

        StringBuilder sb = new StringBuilder();
        for (List<TablePosition> cellsInRow : rows.values()) {
            cellsInRow.sort(Comparator.comparingInt(TablePosition::getColumn));
            for (int i = 0; i < cellsInRow.size(); i++) {
                Object value = table.getColumns().get(cellsInRow.get(i).getColumn())
                        .getCellData(cellsInRow.get(i).getRow());
                if (value != null) sb.append(value);
                if (i < cellsInRow.size() - 1) sb.append("\t");
            }
            sb.append("\n");
        }
        copyToClipboard(sb.toString());
        addMessage("📋 Скопировано " + selectedCells.size() + " ячеек");
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    // ========== ПОИСК В ТАБЛИЦЕ ==========

    private void setupSecuritySearch() {
        TextField searchField = mainController.getSecuritySearchField();
        if (searchField == null) return;
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) filterSecurityTable();
        });
    }

    public void filterSecurityTable() {
        String searchText = mainController.getSecuritySearchField().getText();
        currentSearchText = searchText == null ? "" : searchText.trim().toLowerCase();

        TableView<Scanner_Security.SecurityIssue> table = mainController.getSecurityIssuesTable();
        if (currentSearchText.isEmpty()) {
            table.setItems(FXCollections.observableArrayList(allSecurityIssues));
        } else {
            List<Scanner_Security.SecurityIssue> filtered = allSecurityIssues.stream()
                    .filter(issue ->
                            (issue.functionName != null && issue.functionName.toLowerCase().contains(currentSearchText)) ||
                                    (issue.objectFullName != null && issue.objectFullName.toLowerCase().contains(currentSearchText)))
                    .collect(Collectors.toList());
            table.setItems(FXCollections.observableArrayList(filtered));
        }
        updateSecurityStatsFromCurrentTable();
    }

    public void clearSecuritySearch() {
        mainController.getSecuritySearchField().clear();
        currentSearchText = "";
        filterSecurityTable();
    }

    private void updateSecurityStatsFromCurrentTable() {
        TableView<Scanner_Security.SecurityIssue> table = mainController.getSecurityIssuesTable();
        List<Scanner_Security.SecurityIssue> items = new ArrayList<>(table.getItems());

        long critical = items.stream().filter(i -> "CRITICAL".equals(i.severity)).count();
        long high     = items.stream().filter(i -> "HIGH".equals(i.severity)).count();
        long medium   = items.stream().filter(i -> "MEDIUM".equals(i.severity)).count();

        Platform.runLater(() -> {
            mainController.getSecurityTotalLabel().setText("Всего проблем: " + items.size());
            mainController.getSecurityCriticalLabel().setText("CRITICAL: " + critical);
            mainController.getSecurityHighLabel().setText("HIGH: " + high);
            mainController.getSecurityMediumLabel().setText("MEDIUM: " + medium);
        });
    }

    // ========== СКАНИРОВАНИЕ ==========

    public void runSecurityScan() {
        mainController.getSecurityScanProgress().setVisible(true);

        Task<List<Scanner_Security.SecurityIssue>> task = new Task<>() {
            @Override
            protected List<Scanner_Security.SecurityIssue> call() {
                return Scanner_Security.scanForSecurityIssues();
            }
        };
        task.setOnSucceeded(e -> {
            List<Scanner_Security.SecurityIssue> issues = task.getValue();
            updateSecurityResults(issues);
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("✅ Сканирование безопасности завершено. Найдено: " + issues.size());
        });
        task.setOnFailed(e -> {
            addMessage("❌ Ошибка: " + task.getException().getMessage());
            mainController.getSecurityScanProgress().setVisible(false);
        });
        new Thread(task).start();
    }

    private void updateSecurityResults(List<Scanner_Security.SecurityIssue> issues) {
        allSecurityIssues = new ArrayList<>(issues);
        Platform.runLater(() -> {
            mainController.getSecurityIssuesTable().setItems(FXCollections.observableArrayList(issues));
            updateSecurityStatsFromCurrentTable();
        });
    }

    // ========== КОНВЕРТАЦИЯ РЕЗУЛЬТАТОВ ДРУГИХ СКАНЕРОВ ==========
    // Для сканеров которые не работают напрямую с БД — functionId = 0,
    // поиск текста функции будет по имени+объекту (fallback)

    public void scanTransactions() {
        addMessage("💣 Поиск проблем с транзакциями...");
        mainController.getSecurityScanProgress().setVisible(true);

        Task<List<Scanner_Transactions.TransactionIssue>> task = new Task<>() {
            @Override
            protected List<Scanner_Transactions.TransactionIssue> call() {
                return Scanner_Transactions.scanForTransactionIssues();
            }
        };
        task.setOnSucceeded(e -> {
            List<Scanner_Security.SecurityIssue> issues = task.getValue().stream()
                    .map(i -> new Scanner_Security.SecurityIssue(
                            0, i.type, i.severity, i.description,
                            i.functionName, i.objectFullName, i.moduleType,
                            i.filePath, i.snippet, i.lineNumber, i.recommendation))
                    .collect(Collectors.toList());
            updateSecurityResults(issues);
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("✅ Анализ транзакций завершён. Найдено: " + issues.size());
        });
        task.setOnFailed(e -> {
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("❌ Ошибка: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    public void scanLocks() {
        addMessage("🔐 Поиск проблем с блокировками...");
        mainController.getSecurityScanProgress().setVisible(true);

        Task<List<Scanner_Locks.LockIssue>> task = new Task<>() {
            @Override
            protected List<Scanner_Locks.LockIssue> call() {
                return Scanner_Locks.scanForLockIssues();
            }
        };
        task.setOnSucceeded(e -> {
            List<Scanner_Security.SecurityIssue> issues = task.getValue().stream()
                    .map(i -> new Scanner_Security.SecurityIssue(
                            0, i.type, i.severity, i.description,
                            i.functionName, i.objectFullName, i.moduleType,
                            i.filePath, i.snippet, i.lineNumber, i.recommendation))
                    .collect(Collectors.toList());
            updateSecurityResults(issues);
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("✅ Анализ блокировок завершён. Найдено: " + issues.size());
        });
        task.setOnFailed(e -> {
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("❌ Ошибка: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    public void scanBackgroundJobs() {
        addMessage("🔄 Анализ проблем многопоточности...");
        mainController.getSecurityScanProgress().setVisible(true);

        Task<List<Scanner_BackgroundJobs.BackgroundJobIssue>> task = new Task<>() {
            @Override
            protected List<Scanner_BackgroundJobs.BackgroundJobIssue> call() {
                return Scanner_BackgroundJobs.scanForBackgroundIssues();
            }
        };
        task.setOnSucceeded(e -> {
            List<Scanner_Security.SecurityIssue> issues = task.getValue().stream()
                    .map(i -> new Scanner_Security.SecurityIssue(
                            0, i.type, i.severity, i.description,
                            i.functionName, i.objectFullName, i.moduleType,
                            "", nvl(i.snippet), i.lineNumber, i.recommendation))
                    .collect(Collectors.toList());
            updateSecurityResults(issues);
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("✅ Анализ многопоточности завершён. Найдено: " + issues.size());
        });
        task.setOnFailed(e -> {
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("❌ Ошибка: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    public void scanBadVariableNames() {
        addMessage("🔍 Поиск плохих имён переменных...");
        mainController.getSecurityScanProgress().setVisible(true);

        Task<List<Scanner_BadVariableNames.BadVariableIssue>> task = new Task<>() {
            @Override
            protected List<Scanner_BadVariableNames.BadVariableIssue> call() {
                return Scanner_BadVariableNames.scanForBadVariableNames();
            }
        };
        task.setOnSucceeded(e -> {
            List<Scanner_Security.SecurityIssue> issues = task.getValue().stream()
                    .map(i -> new Scanner_Security.SecurityIssue(
                            0, i.type, i.severity, "Плохое имя переменной: " + i.variableName,
                            i.functionName, i.objectFullName, i.moduleType,
                            i.filePath, i.context, i.lineNumber, i.recommendation))
                    .collect(Collectors.toList());
            updateSecurityResults(issues);
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("✅ Анализ имён завершён. Найдено: " + issues.size());
        });
        task.setOnFailed(e -> {
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("❌ Ошибка: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    public void scanLoopQueries() {
        addMessage("♾️ Поиск точек в циклах...");
        mainController.getSecurityScanProgress().setVisible(true);

        Task<List<Scanner_LoopQueries.LoopQueryIssue>> task = new Task<>() {
            @Override
            protected List<Scanner_LoopQueries.LoopQueryIssue> call() {
                return Scanner_LoopQueries.scanForLoopQueries();
            }
        };
        task.setOnSucceeded(e -> {
            List<Scanner_Security.SecurityIssue> issues = task.getValue().stream()
                    .map(i -> new Scanner_Security.SecurityIssue(
                            0, i.type, i.severity, i.description,
                            i.functionName, i.objectFullName, i.moduleType,
                            "", i.snippet, i.lineNumber, i.recommendation))
                    .collect(Collectors.toList());
            updateSecurityResults(issues);
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("✅ Анализ циклов завершён. Найдено: " + issues.size());
        });
        task.setOnFailed(e -> {
            mainController.getSecurityScanProgress().setVisible(false);
            addMessage("❌ Ошибка: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    // ========== ДЕТАЛИ ВЫБРАННОЙ ПРОБЛЕМЫ ==========

    private void showSecurityFunctionDetails(Scanner_Security.SecurityIssue issue) {
        if (issue == null) return;

        mainController.getSelectedVulnerabilityLabel().setText(issue.type + " — " + issue.severity);
        mainController.getVulnerabilityLocationLabel().setText(
                issue.functionName + " в " + issue.objectFullName + " (строка " + issue.lineNumber + ")");
        mainController.getSecurityDetailText().setText(issue.recommendation);
        mainController.getFunctionInfoLabel().setText("Функция: " + issue.functionName);

        int targetLine = issue.lineNumber;

        Task<String> loadTask = new Task<>() {
            @Override
            protected String call() {
                if (issue.functionId > 0) {
                    // 🔥 Точный поиск по id — сканер безопасности
                    return getFunctionTextById(issue.functionId);
                } else {
                    // Fallback по имени — остальные сканеры
                    return getFunctionTextByName(issue.functionName, issue.objectFullName);
                }
            }
        };

        loadTask.setOnSucceeded(e -> {
            String functionText = loadTask.getValue();
            CodeArea codeArea = mainController.getSecurityFunctionCodeArea();

            if (functionText != null && !functionText.trim().isEmpty()) {
                codeArea.replaceText(functionText);

                // Сначала синтаксис, потом выделение строки
                Platform.runLater(() -> {
                    OneCHighlighter.apply1CColors(codeArea);
                    Platform.runLater(() -> {
                        highlightLine(codeArea, targetLine);
                        scrollToLine(codeArea, targetLine);
                    });
                });
            } else {
                codeArea.replaceText("// Текст функции не найден\n");
                OneCHighlighter.apply1CColors(codeArea);
                mainController.getFunctionInfoLabel().setText("Текст функции не найден");
            }
        });

        loadTask.setOnFailed(e -> {
            mainController.getSecurityFunctionCodeArea().replaceText(
                    "// Ошибка загрузки: " + loadTask.getException().getMessage());
            OneCHighlighter.apply1CColors(mainController.getSecurityFunctionCodeArea());
        });

        new Thread(loadTask).start();
    }

    private void clearSecurityDetails() {
        mainController.getSecurityFunctionCodeArea().clear();
        mainController.getSecurityDetailText().clear();
        mainController.getSelectedVulnerabilityLabel().setText("Выберите проблему для просмотра кода");
        mainController.getVulnerabilityLocationLabel().setText("");
        mainController.getFunctionInfoLabel().setText("");
    }

    // ========== ПОДСВЕТКА И ПРОКРУТКА ==========

    private void highlightLine(CodeArea codeArea, int lineNumber) {
        if (lineNumber <= 0) return;
        Platform.runLater(() -> {
            try {
                String text = codeArea.getText();
                if (text == null || text.isEmpty()) return;
                String[] lines = text.split("\n", -1);
                if (lineNumber > lines.length) return;

                int targetIdx = lineNumber - 1;
                int startPos = 0;
                for (int i = 0; i < targetIdx; i++) startPos += lines[i].length() + 1;
                int endPos = startPos + lines[targetIdx].length();
                if (startPos >= endPos) return;

                OneCHighlighter.apply1CColors(codeArea);
                codeArea.setStyle(startPos, endPos, Collections.singleton("search-highlight-current"));
            } catch (Exception ex) {
                System.err.println("Ошибка выделения строки: " + ex.getMessage());
            }
        });
    }

    private void scrollToLine(CodeArea codeArea, int lineNumber) {
        if (lineNumber <= 0) return;
        Platform.runLater(() -> {
            try {
                String text = codeArea.getText();
                if (text == null || text.isEmpty()) return;
                String[] lines = text.split("\n", -1);
                if (lineNumber > lines.length) return;

                int targetIdx = lineNumber - 1;
                codeArea.showParagraphAtCenter(targetIdx);

                int startPos = 0;
                for (int i = 0; i < targetIdx; i++) startPos += lines[i].length() + 1;
                codeArea.moveTo(startPos);
                codeArea.requestFocus();
            } catch (Exception ex) {
                System.err.println("Ошибка прокрутки: " + ex.getMessage());
            }
        });
    }

    // ========== ЗАГРУЗКА ТЕКСТА ФУНКЦИИ ==========

    // 🔥 Точный поиск по id — для сканера безопасности
    private String getFunctionTextById(int functionId) {
        String sql = "SELECT function_text FROM metadata_functions WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, functionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("function_text");
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки по id: " + e.getMessage());
        }
        return null;
    }

    // Fallback по имени — для остальных сканеров где functionId = 0
    private String getFunctionTextByName(String functionName, String objectFullName) {
        String sql = """
                SELECT mf.function_text
                FROM metadata_functions mf
                JOIN metadata_modules mm ON mf.module_id = mm.id
                WHERE mf.function_name = ? AND mm.object_full_name = ?
                LIMIT 1
                """;
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, functionName);
            ps.setString(2, objectFullName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("function_text");
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки по имени: " + e.getMessage());
        }
        return null;
    }

    // ========== ЭКСПОРТ ==========

    public void exportSecurityReport() {
        TableView<Scanner_Security.SecurityIssue> table = mainController.getSecurityIssuesTable();
        if (table == null || table.getItems().isEmpty()) {
            addMessage("❌ Нет данных для экспорта");
            return;
        }

        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Сохранить отчёт");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV файлы", "*.csv"));
        fc.setInitialFileName("security_report_" +
                java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");

        java.io.File file = fc.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.io.PrintWriter w = new java.io.PrintWriter(file)) {
                    w.println("Уровень;Тип;Функция;Объект;Код;Строка;Рекомендация");
                    for (Scanner_Security.SecurityIssue issue : table.getItems()) {
                        w.printf("%s;%s;%s;%s;%s;%d;%s%n",
                                csv(issue.severity), csv(issue.type),
                                csv(issue.functionName), csv(issue.objectFullName),
                                csv(issue.vulnerableCode), issue.lineNumber,
                                csv(issue.recommendation));
                    }
                }
                return null;
            }
        };
        exportTask.setOnSucceeded(e -> addMessage("✅ Отчёт сохранён: " + file.getName()));
        exportTask.setOnFailed(e -> addMessage("❌ Ошибка экспорта: " + exportTask.getException().getMessage()));
        new Thread(exportTask).start();
    }

    // ========== ПРОЧЕЕ ==========

    public void copyFunctionText() {
        String text = mainController.getSecurityFunctionCodeArea().getText();
        if (text != null && !text.trim().isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            addMessage("📋 Текст функции скопирован");
        }
    }

    public void showSecurityStats() {}

    public void shutdown() {}


    private final ConfigTreeManager configTreeManager = ConfigTreeManager.getInstance();

    private String getIconKeyForObject(String objectFullName, String moduleType) {
        if (objectFullName == null) return "conf";

        // Берём ТОЛЬКО то что слева от первой точки
        // "Обработка.ОперацииЗакрытияМесяца" → "Обработка"
        // "ОбщийМодуль.ОбменМобильныеАвтономныйСервер" → "ОбщийМодуль"
        int dotIndex = objectFullName.indexOf('.');
        String prefix = dotIndex > 0
                ? objectFullName.substring(0, dotIndex)
                : objectFullName;

        return configTreeManager.getIconKeyByRussianType(prefix);
    }




    private void addMessage(String message) {
        Platform.runLater(() -> {
            mainController.getMessagesArea().appendText(message + "\n");
            mainController.getMessagesArea().setScrollTop(Double.MAX_VALUE);
        });
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    private String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(";") || escaped.contains("\n") || escaped.contains("\"")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}