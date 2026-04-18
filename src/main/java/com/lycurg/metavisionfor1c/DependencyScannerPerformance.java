package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.fxmisc.richtext.CodeArea;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TablePosition;
import javafx.collections.ObservableList;


public class DependencyScannerPerformance {

    private final DependencyController mainController;
    private final IconManager iconManager = IconManager.getInstance();

    private PerformanceGraphView performanceGraphView;
    private NestedElementsGraphView nestedElementsGraphView;
    private StackPane graphStackPane;

    // Флаг режима и данные для вложенных элементов
    private boolean isNestedElementsMode = false;
    private List<Scanner_NestedElements.NestedElementIssue> currentNestedIssues = new ArrayList<>();

    // Обработчики выбора строки
    private javafx.beans.value.ChangeListener<Scanner_Performance.PerformanceIssue> performanceSelectionListener;
    private javafx.beans.value.ChangeListener<Scanner_Performance.PerformanceIssue> nestedSelectionListener;

    // ========== ПОИСК ПО МОДУЛЮ ==========
    private CodeSearchHelper performanceCodeSearch;

    // В классе DependencyScannerPerformance, в начало после объявления переменных:
    private String currentSearchText = "";
    private List<Scanner_Performance.PerformanceIssue> allPerformanceIssues = new ArrayList<>();
    private List<Scanner_NestedElements.NestedElementIssue> allNestedIssues = new ArrayList<>();


    public void initialize() {
        setupPerformanceTable();
        setupPerformanceSyntaxHighlighting();
        initializePerformanceStats();
        setupGraphContainer();
        setupPerformanceGraph();
        setupNestedElementsGraph();

        setupPerformanceSearch();

        // ========== ИНИЦИАЛИЗАЦИЯ ПОИСКА ПО МОДУЛЮ ==========
        setupCodeSearch();
    }

    public DependencyScannerPerformance(DependencyController controller) {
        this.mainController = controller;
    }

    // ========== НАСТРОЙКА ПОИСКА ПО МОДУЛЮ ==========
    private void setupCodeSearch() {
        TextField searchField = mainController.getPerformanceCodeSearchField();
        Button prevBtn = mainController.getPerformanceCodeSearchPrevBtn();
        Button nextBtn = mainController.getPerformanceCodeSearchNextBtn();
        Label counter = mainController.getPerformanceCodeSearchCounter();
        CodeArea codeArea = mainController.getPerformanceFunctionCodeArea();

        if (searchField != null && codeArea != null) {
            performanceCodeSearch = new CodeSearchHelper(codeArea, searchField, prevBtn, nextBtn, counter);
            System.out.println("✅ Поиск по модулю на вкладке 5 инициализирован");
        } else {
            System.err.println("⚠️ Не удалось инициализировать поиск на вкладке 5");
        }
    }

    public void searchPrevInCode() {
        if (performanceCodeSearch != null) {
            performanceCodeSearch.navigatePrev();
        }
    }

    public void searchNextInCode() {
        if (performanceCodeSearch != null) {
            performanceCodeSearch.navigateNext();
        }
    }

    // ========== НАСТРОЙКА КОНТЕЙНЕРА ==========
    private void setupGraphContainer() {
        Pane originalContainer = mainController.getPerformanceGraphContainer();
        if (originalContainer == null) {
            System.err.println("❌ Контейнер performanceGraphContainer не найден");
            return;
        }

        graphStackPane = new StackPane();

        // Добавляем ScrollPane для прокрутки
        ScrollPane scrollPane = new ScrollPane(graphStackPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background: white; -fx-background-color: white;");

        originalContainer.getChildren().clear();
        originalContainer.getChildren().add(scrollPane);
        System.out.println("✅ ScrollPane для графов создан");
    }
    // ========== ГРАФ ДЛЯ ЗАПРОСОВ В ЦИКЛАХ ==========
    private void setupPerformanceGraph() {
        performanceGraphView = new PerformanceGraphView();

        performanceGraphView.setOnNodeClickCallback(node -> {
            Platform.runLater(() -> {
                CodeArea codeArea = mainController.getPerformanceFunctionCodeArea();

                // Клик на функцию
                if (node != null && node.functionId > 0 && node.nodeType != PerformanceGraphView.NodeType.CYCLE) {
                    String functionText = loadFunctionTextById(node.functionId);
                    if (functionText != null && !functionText.trim().isEmpty()) {
                        codeArea.replaceText(functionText);
                        OneCHighlighter.apply1CColors(codeArea);

                        // Прокручиваем в начало
                        codeArea.moveTo(0, 0);
                        codeArea.requestFollowCaret();

                        mainController.getPerformanceFunctionInfoLabel().setText(
                                "Функция: " + node.name + " (ID: " + node.functionId + ")"
                        );
                        addMessage("📄 Загружен код функции: " + node.name);
                    }
                }
                // Клик на цикл
                else if (node != null && node.nodeType == PerformanceGraphView.NodeType.CYCLE) {
                    // Получаем полную функцию, содержащую цикл
                    String fullFunctionText = getFullFunctionTextFromCycleId(node.functionId);
                    if (fullFunctionText != null && !fullFunctionText.trim().isEmpty()) {
                        codeArea.replaceText(fullFunctionText);
                        OneCHighlighter.apply1CColors(codeArea);

                        // Переходим к строке цикла
                        int lineNumber = getCycleLineNumber(node.functionId);
                        if (lineNumber > 0) {
                            int lineIndex = lineNumber - 1;
                            codeArea.moveTo(lineIndex, 0);
                            codeArea.requestFollowCaret();
                            mainController.getPerformanceFunctionInfoLabel().setText(
                                    "Цикл: " + node.name + " (строка " + lineNumber + ")"
                            );
                            addMessage("📍 Переход к циклу в строке " + lineNumber);
                        } else {
                            mainController.getPerformanceFunctionInfoLabel().setText(
                                    "Цикл: " + node.name
                            );
                        }
                    } else {
                        // Если нет полной функции, показываем только текст цикла
                        String cycleText = getCycleText(node.functionId);
                        if (cycleText != null && !cycleText.trim().isEmpty()) {
                            codeArea.replaceText(cycleText);
                            OneCHighlighter.apply1CColors(codeArea);
                            mainController.getPerformanceFunctionInfoLabel().setText(
                                    "Цикл (ID: " + node.functionId + ")"
                            );
                        }
                    }
                    addMessage("🔄 Загружен код цикла");
                }
            });
        });

        if (graphStackPane != null) {
            graphStackPane.getChildren().add(performanceGraphView);
        }
    }


    private String getFullFunctionTextFromCycleId(int cycleElementId) {
        String sql = """
        SELECT mf.function_text 
        FROM code_elements ce
        JOIN metadata_functions mf ON ce.function_id = mf.id
        WHERE ce.id = ?
    """;
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cycleElementId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("function_text");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка загрузки функции цикла: " + e.getMessage());
        }
        return null;
    }

    private int getCycleLineNumber(int cycleElementId) {
        String sql = "SELECT start_line FROM code_elements WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cycleElementId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("start_line");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения строки цикла: " + e.getMessage());
        }
        return 0;
    }

    // ========== ГРАФ ДЛЯ ВЛОЖЕННЫХ ЭЛЕМЕНТОВ ==========
    private void setupNestedElementsGraph() {
        nestedElementsGraphView = new NestedElementsGraphView();

        nestedElementsGraphView.setOnNodeClickCallback(node -> {
            Platform.runLater(() -> {
                // Узел ФУНКЦИИ - загружаем полный текст функции
                if (node.nodeType == NestedElementsGraphView.NodeType.FUNCTION ||
                        node.nodeType == NestedElementsGraphView.NodeType.RECURSIVE) {
                    String functionText = getFullFunctionText(node.functionName, node.moduleName);
                    if (functionText != null && !functionText.trim().isEmpty()) {
                        CodeArea codeArea = mainController.getPerformanceFunctionCodeArea();
                        codeArea.replaceText(functionText);
                        OneCHighlighter.apply1CColors(codeArea);

                        // Прокручиваем в начало
                        codeArea.moveTo(0, 0);
                        codeArea.requestFollowCaret();

                        mainController.getPerformanceFunctionInfoLabel().setText(
                                "Функция: " + node.functionName
                        );
                        addMessage("📄 Загружен код функции: " + node.functionName);
                    }
                }
                // Узел СТРУКТУРНОГО ЭЛЕМЕНТА (цикл/транзакция/блокировка)
                else if (node.elementId > 0) {
                    String elementText = getElementTextById(node.elementId);
                    if (elementText != null && !elementText.trim().isEmpty()) {
                        CodeArea codeArea = mainController.getPerformanceFunctionCodeArea();

                        // Загружаем полный текст функции
                        String fullFunctionText = getFullFunctionText(node.functionName, node.moduleName);
                        if (fullFunctionText != null && !fullFunctionText.trim().isEmpty()) {
                            codeArea.replaceText(fullFunctionText);
                            OneCHighlighter.apply1CColors(codeArea);

                            // Находим и прокручиваем к строке
                            if (node.startLine > 0) {
                                int lineIndex = node.startLine - 1;
                                if (lineIndex >= 0) {
                                    // Используем moveTo вместо showParagraphAtTop
                                    codeArea.moveTo(lineIndex, 0);
                                    codeArea.requestFollowCaret();

                                    mainController.getPerformanceFunctionInfoLabel().setText(
                                            getTypeName(node.nodeType) + ": " + node.displayName +
                                                    " (строка " + node.startLine + ")"
                                    );
                                    addMessage("📍 Переход к строке " + node.startLine + ": " + node.displayName);
                                }
                            }
                        } else {
                            codeArea.replaceText(elementText);
                            OneCHighlighter.apply1CColors(codeArea);
                            mainController.getPerformanceFunctionInfoLabel().setText(
                                    getTypeName(node.nodeType) + ": " + node.displayName
                            );
                        }
                    }
                }


            });
        });

        if (graphStackPane != null) {
            graphStackPane.getChildren().add(nestedElementsGraphView);
        }

        showPerformanceGraph(true);
    }

    // Добавить вспомогательный метод
    private String getTypeName(NestedElementsGraphView.NodeType type) {
        switch (type) {
            case CYCLE: return "Цикл";
            case TRANSACTION: return "Транзакция";
            case LOCK: return "Блокировка";
            case FUNCTION: return "Функция";
            case RECURSIVE: return "Рекурсия";
            default: return "Элемент";
        }
    }

    private void showPerformanceGraph(boolean showPerformance) {
        System.out.println("🖼️ showPerformanceGraph: showPerformance=" + showPerformance);

        if (performanceGraphView != null) {
            performanceGraphView.setVisible(showPerformance);
            if (showPerformance) {
                performanceGraphView.toFront();
            }
        }

        if (nestedElementsGraphView != null) {
            nestedElementsGraphView.setVisible(!showPerformance);
            if (!showPerformance) {
                nestedElementsGraphView.toFront();
                nestedElementsGraphView.requestLayout();
            }
        }

        if (graphStackPane != null) {
            graphStackPane.requestLayout();
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    private String getCycleText(int cycleElementId) {
        String sql = "SELECT element_text FROM code_elements WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cycleElementId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("element_text");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка получения текста цикла: " + e.getMessage());
        }
        return null;
    }

    private String getElementTextById(int elementId) {
        String sql = "SELECT element_text FROM code_elements WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, elementId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("element_text");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка загрузки текста: " + e.getMessage());
        }
        return null;
    }

    private String loadFunctionTextById(int functionId) {
        String sql = "SELECT function_text FROM metadata_functions WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, functionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("function_text");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки текста функции: " + e.getMessage());
        }
        return null;
    }

    private String getFunctionTextFromDatabase(String functionName, String moduleName) {
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
            ps.setString(2, moduleName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("function_text");
            }
        } catch (SQLException e) {
            System.err.println("Ошибка загрузки текста функции: " + e.getMessage());
        }
        return null;
    }

    // ========== НАСТРОЙКА ТАБЛИЦЫ ==========
    private void setupPerformanceTable() {
        TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();
        if (table == null) {
            System.err.println("❌ Таблица performanceIssuesTable не найдена");
            return;
        }

        table.getColumns().clear();

        TableColumn<Scanner_Performance.PerformanceIssue, String> iconColumn = new TableColumn<>("");
        iconColumn.setPrefWidth(40);
        iconColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(getIconKeyForObject(
                        cellData.getValue().objectFullName,
                        cellData.getValue().moduleType
                )));
        iconColumn.setCellFactory(col -> new TableCell<>() {
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

        TableColumn<Scanner_Performance.PerformanceIssue, String> severityCol = new TableColumn<>("Уровень");
        severityCol.setPrefWidth(80);
        severityCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().severity));
        severityCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String severity, boolean empty) {
                super.updateItem(severity, empty);
                if (empty || severity == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(severity);
                    if (severity.equals("CRITICAL")) {
                        setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                    } else if (severity.equals("HIGH")) {
                        setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #ffc107;");
                    }
                }
            }
        });

        TableColumn<Scanner_Performance.PerformanceIssue, String> typeCol = new TableColumn<>("Тип");
        typeCol.setPrefWidth(150);
        typeCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().type));

        TableColumn<Scanner_Performance.PerformanceIssue, String> functionCol = new TableColumn<>("Функция");
        functionCol.setPrefWidth(200);
        functionCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().functionName));

        TableColumn<Scanner_Performance.PerformanceIssue, String> objectCol = new TableColumn<>("Объект");
        objectCol.setPrefWidth(200);
        objectCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        configTreeManager.getRussianNameByObjectType(cellData.getValue().objectFullName)
                ));

        TableColumn<Scanner_Performance.PerformanceIssue, String> chainCol = new TableColumn<>("Цепочка");
        chainCol.setPrefWidth(250);
        chainCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().chainInfo));

        TableColumn<Scanner_Performance.PerformanceIssue, Integer> depthCol = new TableColumn<>("Глубина");
        depthCol.setPrefWidth(70);
        depthCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().chainDepth).asObject());
        depthCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer depth, boolean empty) {
                super.updateItem(depth, empty);
                if (empty || depth == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(depth.toString());
                    if (depth >= 4) {
                        setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else if (depth == 3) {
                        setStyle("-fx-background-color: #fd7e14; -fx-text-fill: white; -fx-font-weight: bold;");
                    } else if (depth == 2) {
                        setStyle("-fx-background-color: #ffc107; -fx-text-fill: black;");
                    }
                }
            }
        });

        TableColumn<Scanner_Performance.PerformanceIssue, String> codeCol = new TableColumn<>("Код");
        codeCol.setPrefWidth(300);
        codeCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().problematicCode));

        TableColumn<Scanner_Performance.PerformanceIssue, Integer> lineCol = new TableColumn<>("Строка");
        lineCol.setPrefWidth(60);
        lineCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().lineNumber).asObject());

        table.getColumns().addAll(iconColumn, severityCol, typeCol, functionCol, objectCol,
                chainCol, depthCol, codeCol, lineCol);

        // 🔥 СОЗДАЕМ ОБРАБОТЧИК ДЛЯ РЕЖИМА ПРОИЗВОДИТЕЛЬНОСТИ
        performanceSelectionListener = (obs, oldSelection, newSelection) -> {
            if (newSelection != null && !isNestedElementsMode) {
                showPerformanceFunctionDetails(newSelection);
            }
        };

        // СОЗДАЕМ ОБРАБОТЧИК ДЛЯ РЕЖИМА ВЛОЖЕННЫХ ЭЛЕМЕНТОВ
        nestedSelectionListener = (obs, oldSelection, newSelection) -> {
            if (newSelection != null && isNestedElementsMode && nestedElementsGraphView != null) {
                for (Scanner_NestedElements.NestedElementIssue issue : currentNestedIssues) {
                    if (issue.functionName.equals(newSelection.functionName) &&
                            issue.objectFullName.equals(newSelection.objectFullName) &&
                            issue.lineNumber == newSelection.lineNumber) {
                        showNestedElementDetails(issue);
                        break;
                    }
                }
            }
        };
        // Устанавливаем обработчик по умолчанию
        table.getSelectionModel().selectedItemProperty().addListener(performanceSelectionListener);

        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copyPerformanceTableToClipboard(table);
            }
        });

        setupPerformanceTableCopy();

    }


    private void loadFunctionTextForNestedIssue(Scanner_NestedElements.NestedElementIssue issue) {
        if (issue == null) return;

        // Сначала показываем проблемный код, если есть
        if (issue.problematicCode != null && !issue.problematicCode.isEmpty()) {
            mainController.getPerformanceFunctionCodeArea().replaceText(issue.problematicCode);
            OneCHighlighter.apply1CColors(mainController.getPerformanceFunctionCodeArea());
            mainController.getPerformanceFunctionInfoLabel().setText(
                    issue.elementType + ": " + issue.functionName + " (строка " + issue.lineNumber + ")"
            );
        } else {
            mainController.getPerformanceFunctionCodeArea().replaceText("// Загрузка текста функции...");
        }

        // Асинхронно загружаем полный текст функции
        Task<String> loadTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return getFullFunctionText(issue.functionName, issue.objectFullName);
            }
        };

        loadTask.setOnSucceeded(e -> {
            String functionText = loadTask.getValue();
            Platform.runLater(() -> {
                if (functionText != null && !functionText.trim().isEmpty()) {
                    mainController.getPerformanceFunctionCodeArea().replaceText(functionText);
                    OneCHighlighter.apply1CColors(mainController.getPerformanceFunctionCodeArea());
                    mainController.getPerformanceFunctionInfoLabel().setText(
                            "Функция: " + issue.functionName + " (строка " + issue.lineNumber + ")"
                    );
                } else if (issue.problematicCode == null || issue.problematicCode.isEmpty()) {
                    mainController.getPerformanceFunctionCodeArea().replaceText(
                            "// Текст функции не найден в базе данных\n// Функция: " + issue.functionName
                    );
                    mainController.getPerformanceFunctionInfoLabel().setText("Текст функции не найден");
                }
            });
        });

        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                mainController.getPerformanceFunctionCodeArea().replaceText(
                        "// Ошибка загрузки: " + e.getSource().getException().getMessage()
                );
                mainController.getPerformanceFunctionInfoLabel().setText("Ошибка загрузки");
            });
        });

        new Thread(loadTask).start();
    }

    private void setupPerformanceSyntaxHighlighting() {
        if (mainController.getPerformanceFunctionCodeArea() != null) {
            mainController.getPerformanceFunctionCodeArea().textProperty().addListener((observable, oldValue, newValue) -> {
                OneCHighlighter.apply1CColors(mainController.getPerformanceFunctionCodeArea());
            });
            OneCHighlighter.apply1CColors(mainController.getPerformanceFunctionCodeArea());
        }
    }

    private void initializePerformanceStats() {
        mainController.getPerformanceTotalLabel().setText("Всего проблем: 0");
        mainController.getPerformanceCriticalLabel().setText("CRITICAL: 0");
        mainController.getPerformanceHighLabel().setText("HIGH: 0");
        mainController.getPerformanceMediumLabel().setText("MEDIUM: 0");
    }

    // ========== ПОКАЗ ДЕТАЛЕЙ ДЛЯ ВЛОЖЕННЫХ ЭЛЕМЕНТОВ ==========
    private void showNestedElementDetails(Scanner_NestedElements.NestedElementIssue issue) {
        if (issue == null) return;

        mainController.getSelectedPerformanceLabel().setText(issue.elementType + " - " + issue.severity);
        mainController.getPerformanceLocationLabel().setText(
                issue.functionName + " в " + issue.objectFullName +
                        " (строка " + issue.lineNumber + ", глубина: " + issue.totalDepth + ")");
        mainController.getPerformanceDetailText().setText(
                "🔗 Цепочка вложенности:\n" + issue.chainPath + "\n\n" + issue.recommendation);

        // Загружаем текст функции
        loadFunctionTextForNestedIssue(issue);

        // Строим граф
        if (nestedElementsGraphView != null) {
            nestedElementsGraphView.buildNestedElementsGraph(issue);
        }
    }

    // ========== СКАНЕР ПРОИЗВОДИТЕЛЬНОСТИ (ЗАПРОСЫ В ЦИКЛАХ) ==========
    public void runPerformanceScan() {
        mainController.getPerformanceScanProgress().setVisible(true);

        Task<List<Scanner_Performance.PerformanceIssue>> scanTask = new Task<>() {
            @Override
            protected List<Scanner_Performance.PerformanceIssue> call() {
                return Scanner_Performance.scanForPerformanceIssues();
            }
        };

        scanTask.setOnSucceeded(e -> {
            List<Scanner_Performance.PerformanceIssue> issues = scanTask.getValue();
            updatePerformanceResults(issues);
            mainController.getPerformanceScanProgress().setVisible(false);
            addMessage("✅ Сканирование производительности завершено. Найдено " + issues.size() + " проблем");
        });

        scanTask.setOnFailed(e -> {
            addMessage("❌ Ошибка сканирования: " + scanTask.getException().getMessage());
            mainController.getPerformanceScanProgress().setVisible(false);
        });

        new Thread(scanTask).start();
    }

    private void updatePerformanceResults(List<Scanner_Performance.PerformanceIssue> issues) {
        Platform.runLater(() -> {
            System.out.println("⚡ Найдено проблем производительности: " + issues.size());

            allPerformanceIssues = new ArrayList<>(issues);
            allNestedIssues.clear();

            isNestedElementsMode = false;
            currentSearchText = "";

            TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();

            table.getSelectionModel().selectedItemProperty().removeListener(nestedSelectionListener);
            table.getSelectionModel().selectedItemProperty().addListener(performanceSelectionListener);

            showPerformanceGraph(true);

            if (mainController.getPerformanceSearchField() != null) {
                mainController.getPerformanceSearchField().clear();
            }

            // 🔥 ДОБАВЛЯЕМ МЕТКУ РЕКУРСИИ ДЛЯ ЛЮБОЙ ФУНКЦИИ В ЦЕПОЧКЕ
            for (Scanner_Performance.PerformanceIssue issue : issues) {
                boolean hasRecursion = isAnyFunctionInChainRecursive(issue);

                if (hasRecursion) {
                    // Добавляем метку в chainInfo
                    if (issue.chainInfo == null || issue.chainInfo.isEmpty()) {
                        issue.chainInfo = "🔄 РЕКУРСИЯ В ЦЕПОЧКЕ";
                    } else if (!issue.chainInfo.contains("рекурсия")) {
                        issue.chainInfo = issue.chainInfo + " + рекурсия";
                    }

                    // Повышаем severity
                    if (!"CRITICAL".equals(issue.severity)) {
                        issue.severity = "HIGH";
                    }
                }
            }

            table.setItems(FXCollections.observableArrayList(issues));

            Map<String, Integer> stats = Scanner_Performance.getPerformanceStatistics(issues);
            mainController.getPerformanceTotalLabel().setText("Всего проблем: " + stats.get("TOTAL"));
            mainController.getPerformanceCriticalLabel().setText("CRITICAL: " + stats.get("CRITICAL"));
            mainController.getPerformanceHighLabel().setText("HIGH: " + stats.get("HIGH"));
            mainController.getPerformanceMediumLabel().setText("MEDIUM: " + stats.get("MEDIUM"));

            if (!issues.isEmpty()) {
                table.getSelectionModel().select(0);
            }
        });
    }
    private boolean isFunctionRecursive(int functionId) {
        if (functionId <= 0) return false;
        String sql = "SELECT COUNT(*) FROM recursive_functions WHERE function_id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, functionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            // Таблицы может не быть
        }
        return false;
    }

    private boolean isAnyFunctionInChainRecursive(Scanner_Performance.PerformanceIssue issue) {
        if (issue == null) return false;

        // Проверяем саму функцию
        if (isFunctionRecursive(issue.functionId)) return true;

        // Проверяем всех предков в цепочке
        if (issue.chainFunctionIds != null) {
            for (int funcId : issue.chainFunctionIds) {
                if (isFunctionRecursive(funcId)) return true;
            }
        }

        return false;
    }

    private void showPerformanceFunctionDetails(Scanner_Performance.PerformanceIssue issue) {
        if (issue == null) return;

        showPerformanceGraph(true);

        mainController.getSelectedPerformanceLabel().setText(issue.type + " - " + issue.severity);
        mainController.getPerformanceLocationLabel().setText(
                issue.functionName + " в " + issue.objectFullName +
                        " (строка " + issue.lineNumber + ", глубина циклов: " + issue.chainDepth + ")");

        mainController.getPerformanceDetailText().setText(
                "🔗 Цепочка вложенности:\n" + issue.chainPath + "\n\n" + issue.recommendation);

        if (performanceGraphView != null) {
            performanceGraphView.buildPerformanceGraph(issue);
        }

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return getFunctionTextFromDatabase(issue.functionName, issue.objectFullName);
            }
        };

        task.setOnSucceeded(e -> {
            String functionText = task.getValue();
            if (functionText != null && !functionText.trim().isEmpty()) {
                mainController.getPerformanceFunctionCodeArea().replaceText(functionText);
                OneCHighlighter.apply1CColors(mainController.getPerformanceFunctionCodeArea());
                mainController.getPerformanceFunctionInfoLabel().setText("Функция: " + issue.functionName);
            } else {
                mainController.getPerformanceFunctionCodeArea().replaceText("// Текст функции не найден в базе данных\n");
                OneCHighlighter.apply1CColors(mainController.getPerformanceFunctionCodeArea());
                mainController.getPerformanceFunctionInfoLabel().setText("Текст функции не найден");
            }
        });

        task.setOnFailed(e -> {
            mainController.getPerformanceFunctionCodeArea().replaceText(
                    "// Ошибка загрузки текста функции: " + e.getSource().getException().getMessage()
            );
            OneCHighlighter.apply1CColors(mainController.getPerformanceFunctionCodeArea());
            mainController.getPerformanceFunctionInfoLabel().setText("Ошибка загрузки");
        });

        new Thread(task).start();
    }

    // ========== СКАНЕР ВЛОЖЕННЫХ ЭЛЕМЕНТОВ (БЛОКИРОВКИ/ТРАНЗАКЦИИ) ==========
    public void runNestedElementsScan() {
        System.out.println("🔘 DependencyScannerPerformance.runNestedElementsScan() вызван");

        Platform.runLater(() -> {
            mainController.getPerformanceScanProgress().setVisible(true);
            addMessage("🔍 Сканирование вложенных элементов...");
        });

        Task<List<Scanner_NestedElements.NestedElementIssue>> scanTask = new Task<>() {
            @Override
            protected List<Scanner_NestedElements.NestedElementIssue> call() {
                System.out.println("📊 Запуск Scanner_NestedElements.scanForNestedElements()");
                List<Scanner_NestedElements.NestedElementIssue> result = Scanner_NestedElements.scanForNestedElements();
                System.out.println("✅ Найдено проблем: " + (result != null ? result.size() : 0));
                return result;
            }
        };

        scanTask.setOnSucceeded(e -> {
            List<Scanner_NestedElements.NestedElementIssue> issues = scanTask.getValue();
            updateNestedElementsResults(issues);
            Platform.runLater(() -> {
                mainController.getPerformanceScanProgress().setVisible(false);
                addMessage("✅ Сканирование завершено. Найдено " + issues.size() + " проблем");
            });
        });

        scanTask.setOnFailed(e -> {
            System.err.println("❌ Ошибка: " + scanTask.getException().getMessage());
            scanTask.getException().printStackTrace();
            Platform.runLater(() -> {
                mainController.getPerformanceScanProgress().setVisible(false);
                addMessage("❌ Ошибка сканирования: " + scanTask.getException().getMessage());
            });
        });

        new Thread(scanTask).start();
    }

    private void updateNestedElementsResults(List<Scanner_NestedElements.NestedElementIssue> issues) {
        Platform.runLater(() -> {
            System.out.println("📊 updateNestedElementsResults вызван, issues.size=" + (issues != null ? issues.size() : 0));

            // Сохраняем все данные для поиска
            allNestedIssues = new ArrayList<>(issues);
            allPerformanceIssues.clear();

            // 🔥 ПЕРЕКЛЮЧАЕМ РЕЖИМ
            isNestedElementsMode = true;
            currentNestedIssues = new ArrayList<>(issues);
            currentSearchText = "";

            TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();

            // 🔥 МЕНЯЕМ ОБРАБОТЧИК
            table.getSelectionModel().selectedItemProperty().removeListener(performanceSelectionListener);
            table.getSelectionModel().selectedItemProperty().addListener(nestedSelectionListener);

            // Показываем граф вложенных элементов
            showPerformanceGraph(false);

            // Очищаем поле поиска
            if (mainController.getPerformanceSearchField() != null) {
                mainController.getPerformanceSearchField().clear();
            }

            // 🔥 ОЧИЩАЕМ И ЗАПОЛНЯЕМ ТАБЛИЦУ
            table.getItems().clear();

            if (issues == null || issues.isEmpty()) {
                addMessage("⚠️ Проблем не найдено");
                mainController.getPerformanceTotalLabel().setText("Всего проблем: 0");
                mainController.getPerformanceCriticalLabel().setText("CRITICAL: 0");
                mainController.getPerformanceHighLabel().setText("HIGH: 0");
                mainController.getPerformanceMediumLabel().setText("MEDIUM: 0");
                if (nestedElementsGraphView != null) {
                    nestedElementsGraphView.buildNestedElementsGraph(null);
                }
                mainController.getPerformanceFunctionCodeArea().clear();
                mainController.getPerformanceFunctionInfoLabel().setText("");
                return;
            }

            // 🔥 ЗАПОЛНЯЕМ ТАБЛИЦУ ДАННЫМИ
            for (Scanner_NestedElements.NestedElementIssue issue : issues) {
                String chainString = (issue.allFunctionNames != null && !issue.allFunctionNames.isEmpty())
                        ? String.join(" → ", issue.allFunctionNames)
                        : issue.functionName;

                // Формируем информативную строку для колонки "Цепочка"
                String chainInfo = issue.totalDepth + " уровней, " + issue.functionCount + " функции";
                if (issue.hasRecursion) {
                    chainInfo += ", ⚠️ РЕКУРСИЯ!";
                }

                Scanner_Performance.PerformanceIssue perfIssue = new Scanner_Performance.PerformanceIssue(
                        issue.elementType, issue.severity, issue.description,
                        issue.functionName, issue.objectFullName, issue.moduleType,
                        "",  // filePath
                        chainString,  // problematicCode
                        issue.lineNumber,
                        issue.recommendation,
                        issue.chainPath,
                        issue.totalDepth,  // chainDepth
                        -1,  // functionId
                        chainInfo  // chainInfo
                );
                table.getItems().add(perfIssue);
            }

            // Обновляем статистику
            int critical = (int) issues.stream().filter(i -> "CRITICAL".equals(i.severity)).count();
            int high = (int) issues.stream().filter(i -> "HIGH".equals(i.severity)).count();
            int medium = (int) issues.stream().filter(i -> "MEDIUM".equals(i.severity)).count();

            mainController.getPerformanceTotalLabel().setText("Всего проблем: " + issues.size());
            mainController.getPerformanceCriticalLabel().setText("CRITICAL: " + critical);
            mainController.getPerformanceHighLabel().setText("HIGH: " + high);
            mainController.getPerformanceMediumLabel().setText("MEDIUM: " + medium);

            addMessage("📊 Найдено проблем: " + issues.size() +
                    " (CRITICAL: " + critical + ", HIGH: " + high + ", MEDIUM: " + medium + ")");

            // 🔥 ВЫДЕЛЯЕМ ПЕРВУЮ СТРОКУ В ТАБЛИЦЕ
            if (!issues.isEmpty()) {
                table.getSelectionModel().select(0);
            }
        });
    }

    // ========== ОБЩИЕ МЕТОДЫ ==========
    private void clearPerformanceDetails() {
        mainController.getPerformanceFunctionCodeArea().clear();
        mainController.getPerformanceDetailText().clear();
        mainController.getSelectedPerformanceLabel().setText("Выберите проблему для просмотра кода");
        mainController.getPerformanceLocationLabel().setText("");
        mainController.getPerformanceFunctionInfoLabel().setText("");

        if (performanceGraphView != null) {
            performanceGraphView.buildPerformanceGraph(null);
        }
        if (nestedElementsGraphView != null) {
            nestedElementsGraphView.buildNestedElementsGraph(null);
        }
    }


    // Добавить поле рядом с iconManager
    private final ConfigTreeManager configTreeManager = ConfigTreeManager.getInstance();

    private String getIconKeyForObject(String objectFullName, String moduleType) {
        if (objectFullName == null) return "conf";

        int dotIndex = objectFullName.indexOf('.');
        String prefix = dotIndex > 0
                ? objectFullName.substring(0, dotIndex)
                : objectFullName;

        // Пробуем как русский тип ("Обработка", "ОбщийМодуль"...)
        String iconKey = configTreeManager.getIconKeyByRussianType(prefix);

        // Если не нашли — пробуем как английский DB-тип
        // ("ChartsOfCharacteristicTypes", "ChartsOfAccounts"...)
        if ("conf".equals(iconKey)) {
            iconKey = configTreeManager.getIconKeyByDbObjectType(prefix);
        }

        return iconKey;
    }

    private void copyPerformanceTableToClipboard(TableView<Scanner_Performance.PerformanceIssue> table) {
        StringBuilder clipboardString = new StringBuilder();
        for (TableColumn<?, ?> column : table.getColumns()) {
            if (!column.getText().isEmpty()) {
                clipboardString.append(column.getText()).append("\t");
            }
        }
        clipboardString.append("\n");

        List<Scanner_Performance.PerformanceIssue> selectedItems = table.getSelectionModel().getSelectedItems();
        for (Scanner_Performance.PerformanceIssue issue : selectedItems) {
            clipboardString.append(issue.severity).append("\t")
                    .append(issue.type).append("\t")
                    .append(issue.functionName).append("\t")
                    .append(issue.objectFullName).append("\t")
                    .append("Циклов: " + issue.chainDepth).append("\t")
                    .append(issue.chainDepth).append("\t")
                    .append(issue.problematicCode).append("\t")
                    .append(issue.lineNumber).append("\n");
        }

        if (clipboardString.length() > 0) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(clipboardString.toString());
            clipboard.setContent(content);
            addMessage("📋 Скопировано " + selectedItems.size() + " строк из таблицы");
        }
    }

    private void addMessage(String message) {
        Platform.runLater(() -> {
            mainController.getMessagesArea().appendText(message + "\n");
            mainController.getMessagesArea().setScrollTop(Double.MAX_VALUE);
        });
    }

    public void showPerformanceStats() {
        addMessage("📊 Показать статистику производительности");
    }

    public void exportPerformanceReport() {
        TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();
        List<Scanner_Performance.PerformanceIssue> issues = table.getItems();
        if (issues == null || issues.isEmpty()) {
            addMessage("⚠️ Нет данных для экспорта");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Сохранить отчет в CSV");
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        fileChooser.setInitialFileName("performance_report_" + timestamp + ".csv");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        java.io.File file = fileChooser.showSaveDialog(mainController.getPerformanceIssuesTable().getScene().getWindow());
        if (file == null) {
            addMessage("⚠️ Экспорт отменен");
            return;
        }

        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.io.PrintWriter writer = new java.io.PrintWriter(
                        new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8"))) {
                    writer.println("Уровень;Тип;Функция;Объект;Цепочка;Глубина;Проблемный код;Строка");
                    for (Scanner_Performance.PerformanceIssue issue : issues) {
                        writer.printf("%s;%s;%s;%s;%s;%d;%s;%d%n",
                                escapeCSV(issue.severity), escapeCSV(issue.type),
                                escapeCSV(issue.functionName), escapeCSV(issue.objectFullName),
                                escapeCSV(issue.chainPath != null ? issue.chainPath.substring(0, Math.min(issue.chainPath.length(), 500)) : ""),
                                issue.chainDepth, escapeCSV(issue.problematicCode), issue.lineNumber);
                    }
                }
                Platform.runLater(() -> addMessage("✅ Экспорт завершен: " + file.getName()));
                return null;
            }
        };
        new Thread(exportTask).start();
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains("\"") || value.contains(";") || value.contains("\n")) {
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        return value;
    }

    public void resetPerformanceGraphZoom() {
        if (isNestedElementsMode) {
            if (nestedElementsGraphView != null) {
                nestedElementsGraphView.resetZoom();
                addMessage("🎯 Масштаб графа вложенных элементов сброшен");
            }
        } else {
            if (performanceGraphView != null) {
                performanceGraphView.resetZoom();
                addMessage("🎯 Масштаб графа производительности сброшен");
            }
        }
    }

    public void savePerformanceGraphToPdf() {
        if (isNestedElementsMode) {
            saveNestedGraphToPdf();
        } else {
            savePerformanceGraphToPdfInternal();
        }
    }

    private void savePerformanceGraphToPdfInternal() {
        if (performanceGraphView == null) {
            addMessage("❌ Ошибка: граф производительности не инициализирован");
            return;
        }

        Scanner_Performance.PerformanceIssue currentIssue = mainController.getPerformanceIssuesTable()
                .getSelectionModel().getSelectedItem();

        String currentFunctionName;
        if (currentIssue != null && currentIssue.functionName != null && !currentIssue.functionName.isEmpty()) {
            currentFunctionName = currentIssue.functionName;
        } else {
            currentFunctionName = "Граф_производительности";
        }

        currentFunctionName = currentFunctionName.replace("(", "_").replace(")", "_")
                .replace(" ", "_").replace(":", "_")
                .substring(0, Math.min(currentFunctionName.length(), 50));

        saveGraphToPdf(performanceGraphView, currentFunctionName, "граф_производительности");
    }

    private void saveNestedGraphToPdf() {
        if (nestedElementsGraphView == null) {
            addMessage("❌ Ошибка: граф вложенных элементов не инициализирован");
            return;
        }

        Scanner_NestedElements.NestedElementIssue currentIssue = null;
        if (currentNestedIssues != null && !currentNestedIssues.isEmpty()) {
            int selectedIndex = mainController.getPerformanceIssuesTable().getSelectionModel().getSelectedIndex();
            if (selectedIndex >= 0 && selectedIndex < currentNestedIssues.size()) {
                currentIssue = currentNestedIssues.get(selectedIndex);
            }
        }

        String currentFunctionName;
        if (currentIssue != null && currentIssue.functionName != null && !currentIssue.functionName.isEmpty()) {
            currentFunctionName = currentIssue.functionName;
        } else {
            currentFunctionName = "Граф_вложенных_элементов";
        }

        currentFunctionName = currentFunctionName.replace("(", "_").replace(")", "_")
                .replace(" ", "_").replace(":", "_")
                .substring(0, Math.min(currentFunctionName.length(), 50));

        saveGraphToPdf(nestedElementsGraphView, currentFunctionName, "граф_вложенных_элементов");
    }

    private void saveGraphToPdf(BaseGraphView graphView, String functionName, String graphType) {
        if (graphView == null) {
            addMessage("❌ Ошибка: граф не инициализирован");
            return;
        }

        javafx.scene.image.WritableImage snapshot;
        try {
            String originalStyle = graphView.getStyle();
            graphView.setStyle(originalStyle + "; -fx-background-color: white !important;");
            graphView.snapshot(new javafx.scene.SnapshotParameters(), null);
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.WHITE);
            snapshot = graphView.snapshot(params, null);
            graphView.setStyle(originalStyle);

            if (snapshot == null) {
                addMessage("❌ Не удалось захватить изображение графа");
                return;
            }
        } catch (Exception e) {
            addMessage("❌ Ошибка захвата изображения: " + e.getMessage());
            return;
        }

        java.awt.image.BufferedImage bufferedImage = javafx.embed.swing.SwingFXUtils.fromFXImage(snapshot, null);
        if (bufferedImage == null) {
            addMessage("❌ Ошибка конвертации изображения");
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Сохранить граф в PDF");
        fileChooser.setInitialFileName(functionName + "_" + graphType + ".pdf");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        java.io.File file = fileChooser.showSaveDialog(graphView.getScene().getWindow());
        if (file == null) {
            addMessage("⚠️ Сохранение отменено");
            return;
        }

        final java.awt.image.BufferedImage finalImage = bufferedImage;
        final String finalFileName = file.getAbsolutePath();
        final String finalFunctionName = functionName;

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
                    org.apache.pdfbox.pdmodel.common.PDRectangle pageSize = new org.apache.pdfbox.pdmodel.common.PDRectangle(
                            org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight(),
                            org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth()
                    );
                    org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(pageSize);
                    document.addPage(page);

                    try (org.apache.pdfbox.pdmodel.PDPageContentStream contentStream =
                                 new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {

                        contentStream.setNonStrokingColor(255, 255, 255);
                        contentStream.addRect(0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                        contentStream.fill();

                        org.apache.pdfbox.pdmodel.font.PDFont russianFont = loadRussianFont(document);

                        contentStream.beginText();
                        contentStream.setFont(russianFont, 10);
                        contentStream.newLineAtOffset(20, page.getMediaBox().getHeight() - 20);
                        contentStream.showText("Граф: " + finalFunctionName.replace("_", " "));
                        contentStream.endText();

                        float margin = 15;
                        float pageWidth = page.getMediaBox().getWidth();
                        float pageHeight = page.getMediaBox().getHeight();
                        float availableHeight = pageHeight - 70;
                        float maxImageWidth = pageWidth - (2 * margin);
                        float maxImageHeight = availableHeight;

                        float imageAspectRatio = (float) finalImage.getWidth() / finalImage.getHeight();
                        float containerAspectRatio = maxImageWidth / maxImageHeight;
                        float imageWidth, imageHeight;

                        if (imageAspectRatio > containerAspectRatio) {
                            imageWidth = maxImageWidth;
                            imageHeight = imageWidth / imageAspectRatio;
                        } else {
                            imageHeight = maxImageHeight;
                            imageWidth = imageHeight * imageAspectRatio;
                        }

                        float imageX = margin + (maxImageWidth - imageWidth) / 2;
                        float imageY = 70 + (availableHeight - imageHeight) / 2;

                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(finalImage, "PNG", baos);
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage =
                                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(
                                        document, baos.toByteArray(), "graph");
                        contentStream.drawImage(pdImage, imageX, imageY, imageWidth, imageHeight);
                    }
                    document.save(finalFileName);
                }
                Platform.runLater(() -> addMessage("✅ PDF сохранен: " + new java.io.File(finalFileName).getName()));
                return null;
            }
        };
        new Thread(saveTask).start();
    }

    private org.apache.pdfbox.pdmodel.font.PDFont loadRussianFont(org.apache.pdfbox.pdmodel.PDDocument document) {
        try (java.io.InputStream fontStream = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (fontStream != null) {
                return org.apache.pdfbox.pdmodel.font.PDType0Font.load(document, fontStream);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Не удалось загрузить шрифт");
        }
        return org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA;
    }

    public void shutdown() {
        // Очистка ресурсов
    }

    // Добавить метод для загрузки полного текста функции по имени и объекту
    private String getFullFunctionText(String functionName, String objectFullName) {
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
            if (rs.next()) {
                return rs.getString("function_text");
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки текста функции: " + e.getMessage());
        }
        return null;
    }



    // В классе DependencyScannerPerformance добавить:

    /**
     * Фильтрация таблицы по тексту поиска
     */
    public void filterPerformanceTable() {
        String searchText = mainController.getPerformanceSearchField().getText();
        if (searchText == null) searchText = "";
        currentSearchText = searchText.trim().toLowerCase();

        if (isNestedElementsMode) {
            filterNestedTable();
        } else {
            filterPerformanceTableInternal();
        }
    }

    private void filterPerformanceTableInternal() {
        TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();

        if (currentSearchText.isEmpty()) {
            for (Scanner_Performance.PerformanceIssue issue : allPerformanceIssues) {
                boolean hasRecursion = isAnyFunctionInChainRecursive(issue);
                if (hasRecursion) {
                    if (issue.chainInfo == null || issue.chainInfo.isEmpty()) {
                        issue.chainInfo = "🔄 РЕКУРСИЯ В ЦЕПОЧКЕ";
                    } else if (!issue.chainInfo.contains("рекурсия")) {
                        issue.chainInfo = issue.chainInfo + " + рекурсия";
                    }
                    if (!"CRITICAL".equals(issue.severity)) {
                        issue.severity = "HIGH";
                    }
                }
            }
            table.setItems(FXCollections.observableArrayList(allPerformanceIssues));
        } else {
            List<Scanner_Performance.PerformanceIssue> filtered = allPerformanceIssues.stream()
                    .filter(issue ->
                            (issue.functionName != null && issue.functionName.toLowerCase().contains(currentSearchText)) ||
                                    (issue.objectFullName != null && issue.objectFullName.toLowerCase().contains(currentSearchText))
                    )
                    .collect(Collectors.toList());

            for (Scanner_Performance.PerformanceIssue issue : filtered) {
                boolean hasRecursion = isAnyFunctionInChainRecursive(issue);
                if (hasRecursion) {
                    if (issue.chainInfo == null || issue.chainInfo.isEmpty()) {
                        issue.chainInfo = "🔄 РЕКУРСИЯ В ЦЕПОЧКЕ";
                    } else if (!issue.chainInfo.contains("рекурсия")) {
                        issue.chainInfo = issue.chainInfo + " + рекурсия";
                    }
                    if (!"CRITICAL".equals(issue.severity)) {
                        issue.severity = "HIGH";
                    }
                }
            }
            table.setItems(FXCollections.observableArrayList(filtered));
        }

        updatePerformanceStatsFromCurrentTable();
    }


    private void filterNestedTable() {
        TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();

        if (currentSearchText.isEmpty()) {
            List<Scanner_Performance.PerformanceIssue> perfIssues = new ArrayList<>();
            for (Scanner_NestedElements.NestedElementIssue issue : allNestedIssues) {
                String chainString = (issue.allFunctionNames != null && !issue.allFunctionNames.isEmpty())
                        ? String.join(" → ", issue.allFunctionNames)
                        : issue.functionName;

                String chainInfo = issue.totalDepth + " уровней, " + issue.functionCount + " функции";
                if (issue.hasRecursion) {
                    chainInfo += ", ⚠️ РЕКУРСИЯ!";
                }

                Scanner_Performance.PerformanceIssue perfIssue = new Scanner_Performance.PerformanceIssue(
                        issue.elementType, issue.severity, issue.description,
                        issue.functionName, issue.objectFullName, issue.moduleType,
                        "",  // filePath
                        chainString,  // problematicCode
                        issue.lineNumber,
                        issue.recommendation,
                        issue.chainPath,
                        issue.totalDepth,  // chainDepth
                        -1,  // functionId
                        chainInfo  // chainInfo
                );
                perfIssues.add(perfIssue);
            }
            table.setItems(FXCollections.observableArrayList(perfIssues));
        } else {
            List<Scanner_Performance.PerformanceIssue> filtered = new ArrayList<>();
            for (Scanner_NestedElements.NestedElementIssue issue : allNestedIssues) {
                String chainString = (issue.allFunctionNames != null && !issue.allFunctionNames.isEmpty())
                        ? String.join(" → ", issue.allFunctionNames)
                        : issue.functionName;

                // Поиск по цепочке функций (колонка "Код") ИЛИ по объекту
                if ((chainString != null && chainString.toLowerCase().contains(currentSearchText)) ||
                        (issue.objectFullName != null && issue.objectFullName.toLowerCase().contains(currentSearchText))) {

                    String chainInfo = issue.totalDepth + " уровней, " + issue.functionCount + " функции";
                    if (issue.hasRecursion) {
                        chainInfo += ", ⚠️ РЕКУРСИЯ!";
                    }

                    Scanner_Performance.PerformanceIssue perfIssue = new Scanner_Performance.PerformanceIssue(
                            issue.elementType, issue.severity, issue.description,
                            issue.functionName, issue.objectFullName, issue.moduleType,
                            "",  // filePath
                            chainString,  // problematicCode
                            issue.lineNumber,
                            issue.recommendation,
                            issue.chainPath,
                            issue.totalDepth,  // chainDepth
                            -1,  // functionId
                            chainInfo  // chainInfo
                    );
                    filtered.add(perfIssue);
                }
            }
            table.setItems(FXCollections.observableArrayList(filtered));
        }

        updatePerformanceStatsFromCurrentTable();
    }

    private void updatePerformanceStatsFromCurrentTable() {
        TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();
        List<Scanner_Performance.PerformanceIssue> currentItems = table.getItems();

        if (currentItems == null || currentItems.isEmpty()) {
            Platform.runLater(() -> {
                mainController.getPerformanceTotalLabel().setText("Всего проблем: 0");
                mainController.getPerformanceCriticalLabel().setText("CRITICAL: 0");
                mainController.getPerformanceHighLabel().setText("HIGH: 0");
                mainController.getPerformanceMediumLabel().setText("MEDIUM: 0");
            });
            return;
        }

        long critical = currentItems.stream().filter(i -> "CRITICAL".equals(i.severity)).count();
        long high = currentItems.stream().filter(i -> "HIGH".equals(i.severity)).count();
        long medium = currentItems.stream().filter(i -> "MEDIUM".equals(i.severity)).count();

        Platform.runLater(() -> {
            mainController.getPerformanceTotalLabel().setText("Всего проблем: " + currentItems.size());
            mainController.getPerformanceCriticalLabel().setText("CRITICAL: " + critical);
            mainController.getPerformanceHighLabel().setText("HIGH: " + high);
            mainController.getPerformanceMediumLabel().setText("MEDIUM: " + medium);
        });
    }


    /**
     * Очистка поиска
     */
    public void clearPerformanceSearch() {
        Platform.runLater(() -> {
            mainController.getPerformanceSearchField().clear();
            currentSearchText = "";
            filterPerformanceTable();
        });
    }

    /**
     * Настройка поля поиска (вызывается в initialize)
     */
    private void setupPerformanceSearch() {
        TextField searchField = mainController.getPerformanceSearchField();
        if (searchField == null) return;

        // Поиск по Enter
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                filterPerformanceTable();
            }
        });
    }


    private void setupPerformanceTableCopy() {
        TableView<Scanner_Performance.PerformanceIssue> table = mainController.getPerformanceIssuesTable();
        if (table == null) return;

        // По умолчанию - выделение строк
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Контекстное меню
        ContextMenu contextMenu = new ContextMenu();

        MenuItem copyItem = new MenuItem("📋 Копировать");
        copyItem.setOnAction(e -> copySelectionToClipboard(table));

        MenuItem copyRowItem = new MenuItem("📋 Копировать строку");
        copyRowItem.setOnAction(e -> copyCurrentRowToClipboard(table));

        MenuItem copyCellItem = new MenuItem("📋 Копировать ячейку");
        copyCellItem.setOnAction(e -> copyCurrentCellToClipboard(table));

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem selectRowModeItem = new MenuItem("✓ Режим: выделение строк");
        selectRowModeItem.setOnAction(e -> setRowSelectionMode(table));

        MenuItem selectCellModeItem = new MenuItem("☐ Режим: выделение ячеек");
        selectCellModeItem.setOnAction(e -> setCellSelectionMode(table));

        contextMenu.getItems().addAll(copyItem, copyRowItem, copyCellItem, separator,
                selectRowModeItem, selectCellModeItem);
        table.setContextMenu(contextMenu);

        // Ctrl+C - копирует то, что выделено (ячейки или строки)
        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copySelectionToClipboard(table);
                event.consume();
            }
            // Ctrl+R - переключить режим выделения строк
            if (event.isControlDown() && event.getCode() == KeyCode.R) {
                if (table.getSelectionModel().isCellSelectionEnabled()) {
                    setRowSelectionMode(table);
                } else {
                    setCellSelectionMode(table);
                }
                event.consume();
            }
        });

        // Визуальная индикация режима в статусной строке
        updateSelectionModeStatus(table);
    }

    private void setRowSelectionMode(TableView<Scanner_Performance.PerformanceIssue> table) {
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        addMessage("📌 Режим выделения: строки (можно выделять Ctrl+Click / Shift+Click)");
        updateSelectionModeStatus(table);
    }

    private void setCellSelectionMode(TableView<Scanner_Performance.PerformanceIssue> table) {
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        addMessage("🔲 Режим выделения: ячейки (Ctrl+C для копирования)");
        updateSelectionModeStatus(table);
    }

    private void updateSelectionModeStatus(TableView<Scanner_Performance.PerformanceIssue> table) {
        Platform.runLater(() -> {
            if (mainController.getPerformanceLocationLabel() != null) {
                String mode = table.getSelectionModel().isCellSelectionEnabled() ? "🔲 режим: ячейки" : "📌 режим: строки";
                mainController.getPerformanceLocationLabel().setText(mode + " | Ctrl+R - переключить");
            }
        });
    }

    private void copySelectionToClipboard(TableView<Scanner_Performance.PerformanceIssue> table) {
        if (table.getSelectionModel().isCellSelectionEnabled()) {
            copySelectedCellsToClipboard(table);
        } else {
            copySelectedRowsToClipboard(table);
        }
    }

    private void copySelectedRowsToClipboard(TableView<Scanner_Performance.PerformanceIssue> table) {
        ObservableList<Scanner_Performance.PerformanceIssue> selectedRows = table.getSelectionModel().getSelectedItems();

        if (selectedRows.isEmpty()) {
            addMessage("⚠️ Нет выделенных строк");
            return;
        }

        StringBuilder sb = new StringBuilder();

        // Заголовки колонок
        for (TableColumn<Scanner_Performance.PerformanceIssue, ?> column : table.getColumns()) {
            String header = column.getText();
            if (header != null && !header.isEmpty()) {
                sb.append(header).append("\t");
            }
        }
        sb.append("\n");

        // Данные
        for (Scanner_Performance.PerformanceIssue issue : selectedRows) {
            sb.append(issue.severity).append("\t")
                    .append(issue.type).append("\t")
                    .append(issue.functionName != null ? issue.functionName : "").append("\t")
                    .append(issue.objectFullName != null ? issue.objectFullName : "").append("\t")
                    .append(issue.chainPath != null ? issue.chainPath : "").append("\t")
                    .append(issue.chainDepth).append("\t")
                    .append(issue.problematicCode != null ? issue.problematicCode : "").append("\t")
                    .append(issue.lineNumber).append("\n");
        }

        copyToClipboard(sb.toString());
        addMessage("📋 Скопировано " + selectedRows.size() + " строк");
    }

    private void copyCurrentRowToClipboard(TableView<Scanner_Performance.PerformanceIssue> table) {
        Scanner_Performance.PerformanceIssue selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            addMessage("⚠️ Нет выделенной строки");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(selected.severity).append("\t")
                .append(selected.type).append("\t")
                .append(selected.functionName != null ? selected.functionName : "").append("\t")
                .append(selected.objectFullName != null ? selected.objectFullName : "").append("\t")
                .append(selected.chainPath != null ? selected.chainPath : "").append("\t")
                .append(selected.chainDepth).append("\t")
                .append(selected.problematicCode != null ? selected.problematicCode : "").append("\t")
                .append(selected.lineNumber);

        copyToClipboard(sb.toString());
        addMessage("📋 Скопирована строка: " + selected.functionName);
    }

    private void copyCurrentCellToClipboard(TableView<Scanner_Performance.PerformanceIssue> table) {
        TablePosition<?, ?> focusedCell = table.getFocusModel().getFocusedCell();
        if (focusedCell == null) {
            addMessage("⚠️ Нет выделенной ячейки");
            return;
        }

        Object value = table.getColumns().get(focusedCell.getColumn()).getCellData(focusedCell.getRow());
        if (value != null) {
            copyToClipboard(value.toString());
            addMessage("📋 Скопирована ячейка: " + value.toString().substring(0, Math.min(50, value.toString().length())) + "...");
        } else {
            addMessage("⚠️ Ячейка пуста");
        }
    }

    private void copySelectedCellsToClipboard(TableView<Scanner_Performance.PerformanceIssue> table) {
        ObservableList<TablePosition> selectedCells = table.getSelectionModel().getSelectedCells();

        if (selectedCells.isEmpty()) {
            addMessage("⚠️ Нет выделенных ячеек");
            return;
        }

        // Группируем по строкам
        Map<Integer, List<TablePosition>> rows = new HashMap<>();
        for (TablePosition cell : selectedCells) {
            rows.computeIfAbsent(cell.getRow(), k -> new ArrayList<>()).add(cell);
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<Integer, List<TablePosition>> entry : rows.entrySet()) {
            List<TablePosition> cellsInRow = entry.getValue();
            cellsInRow.sort((a, b) -> Integer.compare(a.getColumn(), b.getColumn()));

            for (int i = 0; i < cellsInRow.size(); i++) {
                TablePosition cell = cellsInRow.get(i);
                Object value = table.getColumns().get(cell.getColumn()).getCellData(cell.getRow());
                if (value != null) {
                    sb.append(value.toString());
                }
                if (i < cellsInRow.size() - 1) {
                    sb.append("\t");
                }
            }
            sb.append("\n");
        }

        copyToClipboard(sb.toString());
        addMessage("📋 Скопировано " + selectedCells.size() + " ячеек");
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }





}