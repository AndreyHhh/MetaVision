package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;


//# Главный контроллер JavaFX - центральный узел управления 6 вкладками и их компонентами
public class DependencyController {



    // Контроллеры
    private DependencyCodeAnalyzer codeAnalyzer;
    private DependencyWorkbench workbench;
    private DependencyFunctionSearch functionSearch;
    private DependencyScannerSecurity securityScanner;
    private DependencyScannerPerformance performanceScanner;
    private DependencyProblematicCode problematicCode;
    private DependencySpellChecker spellChecker;
    private DependencyStatistics statistics;

    // Граф
    private ElementGraphView elementGraphView;
    private ObservableList<FunctionInfo> functionsList = FXCollections.observableArrayList();

    // ========== ОБЩИЕ ЭЛЕМЕНТЫ ==========
    @FXML public TextField configPathField;
    @FXML public ProgressBar loadProgress;
    @FXML public Label loadStatus;
    @FXML public SplitPane verticalSplitPane;
    @FXML public TextArea messagesArea;
    @FXML private TabPane tabPane;
    @FXML public ComboBox<String> graphStyleComboBox;

    // ========== ВКЛАДКА 1: АНАЛИЗАТОР КОДА ==========

    @FXML
    private Button loadButton;

    @FXML
    private Button configPathButton;
    @FXML public TreeView<String> classTree;
    @FXML public TextField searchField;
    @FXML public Button clearSearchButton;
    @FXML public TableView<FunctionInfo> functionsTable;
    @FXML public Label functionsCountLabel;
    @FXML public Label selectedFunctionLabel;
    @FXML public VBox elementGraphContainer;
    @FXML public CodeArea moduleCodeArea;
    @FXML public Button backButton;

    @FXML private SplitPane messagesSplitPane;

    // 🔥 НОВЫЕ ПОЛЯ ДЛЯ ТАБЛИЦЫ ВЫЗЫВАЮЩИХ
    @FXML private TableView<FunctionSearchResult> callersTable;
    @FXML private Label callersCountLabel;
    @FXML private Label callersTitleLabel;

    // ========== ПОИСК НА ВКЛАДКЕ 1 ==========
    @FXML public TextField codeAnalyzerSearchField;
    @FXML public Button codeAnalyzerSearchPrevBtn;
    @FXML public Button codeAnalyzerSearchNextBtn;
    @FXML public Label codeAnalyzerSearchCounter;

    // ========== ВКЛАДКА 2: РАБОТА С МОДУЛЕМ ==========
    @FXML public CodeArea workbenchCodeArea;
    @FXML public TextArea workbenchLogArea;
    @FXML public Label workbenchStatusLabel;
    @FXML public Label workbenchStatsLabel;
    @FXML public TextArea validationResultsArea;


    @FXML public TextField workbenchCodeSearchField;
    @FXML public Button workbenchCodeSearchPrevBtn;
    @FXML public Button workbenchCodeSearchNextBtn;
    @FXML public Label workbenchCodeSearchCounter;

    // ========== ПОИСК НА ВКЛАДКЕ 2 ==========
    @FXML public TextField workbenchSearchField;
    @FXML public Button workbenchSearchPrevBtn;
    @FXML public Button workbenchSearchNextBtn;
    @FXML public Label workbenchSearchCounter;

    @FXML private Button queryToTextButton;
    @FXML private Button textToQueryButton;
    @FXML private Button validateCodeButton;
    @FXML private Button checkSpellingButton;
    @FXML private Button formatCodeButton;

    // ========== ВКЛАДКА 3: ПОИСК ПО ФУНКЦИЯМ ==========
    @FXML public TextField functionTextSearchField;
    @FXML public Label searchResultsCount;
    @FXML public Label searchTimeLabel;
    @FXML public TableView<FunctionSearchResult> searchResultsTable;
    @FXML public CodeArea functionDetailCodeArea;


    @FXML private SplitPane searchResultsSplitPane;  // если нужно управлять разделителем
    @FXML private VBox functionDetailContainer;       // если нужен доступ к контейнеру

    @FXML private TextField searchTabCodeSearchField;
    @FXML private Button searchTabCodeSearchPrevBtn;
    @FXML private Button searchTabCodeSearchNextBtn;
    @FXML private Label searchTabCodeSearchCounter;

    // ========== ПОИСК НА ВКЛАДКЕ 3 ==========
    @FXML public TextField searchTabSearchField;
    @FXML public Button searchTabSearchPrevBtn;
    @FXML public Button searchTabSearchNextBtn;
    @FXML public Label searchTabSearchCounter;




    // ========== ВКЛАДКА 4: СКАНЕР БЕЗОПАСНОСТИ ==========
    @FXML public TableView<Scanner_Security.SecurityIssue> securityIssuesTable;
    @FXML public CodeArea securityFunctionCodeArea;
    @FXML public Label selectedVulnerabilityLabel;
    @FXML public Label vulnerabilityLocationLabel;
    @FXML public Label functionInfoLabel;
    @FXML public TextArea securityDetailText;
    @FXML public ProgressBar securityScanProgress;
    @FXML public Label securityTotalLabel;
    @FXML public Label securityCriticalLabel;
    @FXML public Label securityHighLabel;
    @FXML public Label securityMediumLabel;

    @FXML
    private TextField securitySearchField;
    @FXML
    private Button securityExportButton;

    @FXML
    private Button securityBackgroundJobsButton;

    @FXML
    private Label securityStatusLabel;

    @FXML
    private Button securityPerformanceButton;




    @FXML
    private Button securityErrorsButton;

    // ========== ПОИСК НА ВКЛАДКЕ 4 ==========
    @FXML public TextField securityCodeSearchField;
    @FXML public Button securityCodeSearchPrevBtn;
    @FXML public Button securityCodeSearchNextBtn;
    @FXML public Label securityCodeSearchCounter;


    @FXML private Button securityButton;
    @FXML private Button backgroundJobsButton;
    @FXML private Button badNamesButton;
    @FXML private Button transactionsButton;
    @FXML private Button locksButton;
    @FXML private Button loopQueriesButton;

    // ========== ВКЛАДКА 5: ПРОИЗВОДИТЕЛЬНОСТЬ ==========
    @FXML public TableView<Scanner_Performance.PerformanceIssue> performanceIssuesTable;
    @FXML public CodeArea performanceFunctionCodeArea;
    @FXML public Label selectedPerformanceLabel;
    @FXML public Label performanceLocationLabel;
    @FXML public Label performanceFunctionInfoLabel;
    @FXML public TextArea performanceDetailText;
    @FXML public ProgressBar performanceScanProgress;
    @FXML public Label performanceTotalLabel;
    @FXML public Label performanceCriticalLabel;
    @FXML public Label performanceHighLabel;
    @FXML public Label performanceMediumLabel;

    // В конце объявлений @FXML добавить:
    @FXML private TextField performanceSearchField;
    @FXML private Button performanceSearchButton;
    @FXML private Button performanceClearSearchButton;


    @FXML public Button scanNestedElementsButton;


    // ========== ВКЛАДКА 6: ПРОБЛЕМНЫЙ КОД ==========
    @FXML public TableView<DependencyProblematicCode.CodeProblem> problemIssuesTable;
    @FXML public CodeArea problemFunctionCodeArea;
    @FXML public Label selectedProblemLabel;
    @FXML public Label problemLocationLabel;
    @FXML public Label problemFunctionInfoLabel;
    @FXML public TextArea problemDetailText;
    @FXML public ProgressBar problemScanProgress;
    @FXML public Label problemTotalLabel;
    @FXML public Label problemCriticalLabel;
    @FXML public Label problemHighLabel;
    @FXML public Label problemMediumLabel;
    @FXML public Label problemLowLabel;


    @FXML private VBox performanceGraphContainer;
    @FXML private Button resetPerformanceGraphButton;

    // ========== ПОИСК НА ВКЛАДКЕ 5 ==========
    @FXML public TextField performanceCodeSearchField;
    @FXML public Button performanceCodeSearchPrevBtn;
    @FXML public Button performanceCodeSearchNextBtn;
    @FXML public Label performanceCodeSearchCounter;

    @FXML
    private Button queryInLoopButton;


    @FXML
    public void initialize() {
        // Создаем контроллеры
        codeAnalyzer = new DependencyCodeAnalyzer(this);
        workbench = new DependencyWorkbench(this);
        functionSearch = new DependencyFunctionSearch(this);
        securityScanner = new DependencyScannerSecurity(this);
        performanceScanner = new DependencyScannerPerformance(this);
        problematicCode = new DependencyProblematicCode(this);
        spellChecker = new DependencySpellChecker(this);
        statistics = new DependencyStatistics(this);

        // Инициализируем все
        codeAnalyzer.initialize();
        workbench.initialize();
        functionSearch.initialize();
        securityScanner.initialize();
        performanceScanner.initialize();
        problematicCode.initialize();


        // Enter на вкладке 1 (поиск по дереву)
        searchField.setOnAction(event -> onSearchClick());
        // Enter на вкладке 3
        functionTextSearchField.setOnAction(event -> searchInFunctions());

        // Инициализация ComboBox со стилями графа
        initializeGraphStyleComboBox();

        // Загрузка настроек при показе окна
        classTree.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() != null) {
                newScene.getWindow().setOnShown(event -> {
                    if (codeAnalyzer != null) {
                        System.out.println("🪟 Окно показано, загружаем настройки...");
                        codeAnalyzer.loadNastroek();
                    }
                });
            }
        });



        if (resetPerformanceGraphButton != null) {
            resetPerformanceGraphButton.setOnAction(e -> {
                if (performanceScanner != null) {
                    performanceScanner.resetPerformanceGraphZoom();
                }
            });
        }

        // 🔥 ЗАГРУЖАЕМ СТАТИСТИКУ В 5-Ю ВКЛАДКУ
        Platform.runLater(() -> {
            if (tabPane != null && tabPane.getTabs().size() >= 6) {  // 🔥 МЕНЯЕМ 5 НА 6
                Tab statisticsTab = tabPane.getTabs().get(5); // 6-я вкладка (индекс 5)

                // Убираем старое содержимое "Вкладка в разработке"
                statisticsTab.setContent(null);

                // Добавляем реальную статистику
                try {
                    statisticsTab.setContent(statistics.createStatisticsTab());
                } catch (Exception e) {
                    System.err.println("❌ Ошибка создания статистики: " + e.getMessage());
                    statisticsTab.setContent(new Label("Ошибка загрузки статистики"));
                }
            } else {
                System.err.println("❌ Вкладок меньше 6! Всего: " + (tabPane != null ? tabPane.getTabs().size() : "null"));
            }
        });

        // 🔥 ДОБАВЛЯЕМ ЗДЕСЬ: слушатель для загрузки настроек при показе окна
        classTree.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && newScene.getWindow() != null) {
                newScene.getWindow().setOnShown(event -> {
                    if (codeAnalyzer != null) {
                        System.out.println("🪟 Окно показано, загружаем настройки...");
                        codeAnalyzer.loadNastroek();
                    }
                });
            }
        });



        // Настройка панели сообщений
        if (messagesSplitPane != null) {
            // Устанавливаем начальную позицию (85% - вкладки, 15% - сообщения)
            Platform.runLater(() -> {
                messagesSplitPane.setDividerPosition(0, 0.90);
            });
        }







//описание кнопок

        //5вкл
        queryInLoopButton.setTooltip(new Tooltip(HelpContent.getPerformanceButtonsHelp().split("\\n\\n")[0]));
        scanNestedElementsButton.setTooltip(new Tooltip(HelpContent.getPerformanceButtonsHelp().split("\\n\\n")[1]));

        //4вкл
        String[] securityTips = HelpContent.getSecurityButtonsHelp().split("\\n\\n");
        securityButton.setTooltip(new Tooltip(securityTips[0]));
        backgroundJobsButton.setTooltip(new Tooltip(securityTips[1]));
        badNamesButton.setTooltip(new Tooltip(securityTips[2]));
        transactionsButton.setTooltip(new Tooltip(securityTips[3]));
        locksButton.setTooltip(new Tooltip(securityTips[4]));
        loopQueriesButton.setTooltip(new Tooltip(securityTips[5]));

        //2вкл
        String[] workbenchTips = HelpContent.getWorkbenchButtonsHelp().split("\\n\\n");
        queryToTextButton.setTooltip(new Tooltip(workbenchTips[0]));
        textToQueryButton.setTooltip(new Tooltip(workbenchTips[1]));
        validateCodeButton.setTooltip(new Tooltip(workbenchTips[2]));
        checkSpellingButton.setTooltip(new Tooltip(workbenchTips[3]));
        formatCodeButton.setTooltip(new Tooltip(workbenchTips[4]));
    }


    // ========== МЕТОДЫ ПОИСКА ДЛЯ ВСЕХ ВКЛАДОК ==========

    @FXML
    public void onSearchTabSearchPrev() {
        if (functionSearch != null) {
            functionSearch.searchPrevInCode();
        }
    }

    @FXML
    public void onSearchTabSearchNext() {
        if (functionSearch != null) {
            functionSearch.searchNextInCode();
        }
    }

    @FXML
    public void onSecurityCodeSearchPrev() {
        if (securityScanner != null) {
            securityScanner.searchPrevInCode();
        }
    }

    @FXML
    public void onSecurityCodeSearchNext() {
        if (securityScanner != null) {
            securityScanner.searchNextInCode();
        }
    }

    @FXML
    public void onPerformanceCodeSearchPrev() {
        if (performanceScanner != null) {
            performanceScanner.searchPrevInCode();
        }
    }

    @FXML
    public void onPerformanceCodeSearchNext() {
        if (performanceScanner != null) {
            performanceScanner.searchNextInCode();
        }
    }


    /**
     * Загружает список функций, которые вызывают указанную функцию
     */
    public void loadCallersForFunction(int functionId, String functionName) {
        Task<List<FunctionSearchResult>> task = new Task<>() {
            @Override
            protected List<FunctionSearchResult> call() throws Exception {
                List<FunctionSearchResult> results = new ArrayList<>();

                String sql = """
                SELECT 
                    mm.object_full_name as object_name,
                    mm.module_type as module_type,
                    mf.function_name as caller_function_name,
                    ce.element_text as context,
                    ce.start_line as line_number,
                    ce.function_id as caller_function_id
                FROM code_elements ce
                JOIN metadata_functions mf ON ce.function_id = mf.id
                JOIN metadata_modules mm ON mf.module_id = mm.id
                WHERE ce.element_type = 'ВызовФункции'
                  AND ce.called_function_id = ?
                ORDER BY mm.object_full_name, mf.function_name
                """;

                try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
                     PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setInt(1, functionId);
                    ResultSet rs = ps.executeQuery();

                    while (rs.next()) {
                        String objectName = rs.getString("object_name");
                        String moduleType = rs.getString("module_type");
                        String callerFunctionName = rs.getString("caller_function_name");
                        String context = rs.getString("context");
                        int lineNumber = rs.getInt("line_number");
                        int callerFunctionId = rs.getInt("caller_function_id");

                        // Используем конструктор, который у вас есть
                        FunctionSearchResult result = new FunctionSearchResult(
                                objectName,      // objectName
                                moduleType,      // functionType (будет использован как тип)
                                callerFunctionName, // functionName
                                context,         // context
                                "",              // fullText (пустая строка)
                                ""               // filePath (пустая строка)
                        );

                        // Устанавливаем дополнительные поля через сеттеры (нужно добавить в класс)
                        result.setFunctionId(callerFunctionId);
                        result.setLineNumber(lineNumber);
                        result.setModuleType(moduleType);

                        results.add(result);
                    }
                } catch (SQLException e) {
                    System.err.println("❌ Ошибка загрузки вызывающих функций: " + e.getMessage());
                    e.printStackTrace();
                }

                return results;
            }
        };

        task.setOnSucceeded(e -> {
            List<FunctionSearchResult> callers = task.getValue();
            updateCallersTable(callers);

            if (callers.isEmpty()) {
                callersCountLabel.setText("Найдено: 0 (никто не вызывает " + functionName + ")");
            } else {
                callersCountLabel.setText("Найдено: " + callers.size());
            }
        });

        task.setOnFailed(e -> {
            System.err.println("❌ Ошибка: " + task.getException().getMessage());
            callersCountLabel.setText("Ошибка загрузки");
        });

        new Thread(task).start();
    }


    @FXML
    public void savePerformanceGraphToPdf() {
        if (performanceScanner != null) {
            performanceScanner.savePerformanceGraphToPdf();
        }
    }
    /**
     * Обновляет таблицу вызывающих функций
     */
    private void updateCallersTable(List<FunctionSearchResult> callers) {
        Platform.runLater(() -> {
            if (callersTable != null) {
                callersTable.getItems().setAll(callers);
            }
            if (callersCountLabel != null) {
                if (callers.isEmpty()) {
                    callersCountLabel.setText("Найдено: 0 (вызовы не найдены)");
                } else {
                    callersCountLabel.setText("Найдено: " + callers.size());
                }
            }
        });
    }

    // 🔥 ДОБАВИТЬ ЭТИ ГЕТТЕРЫ:
    public VBox getPerformanceGraphContainer() {
        return performanceGraphContainer;
    }

    public Button getResetPerformanceGraphButton() {
        return resetPerformanceGraphButton;
    }

    // 🔥 ДОБАВИТЬ МЕТОД ДЛЯ СБРОСА МАСШТАБА ГРАФА:
    @FXML
    private void resetPerformanceGraphZoom() {
        // Этот метод будет вызываться из FXML
        // Реализация будет в DependencyScannerPerformance
    }

    // ========== МЕТОДЫ ПРОИЗВОДИТЕЛЬНОСТИ ==========
    @FXML
    public void runPerformanceScan() {
        performanceScanner.runPerformanceScan();
    }

    @FXML
    public void showPerformanceStats() {
        performanceScanner.showPerformanceStats();
    }


    @FXML
    public void exportPerformanceReport() {
        performanceScanner.exportPerformanceReport();
    }



    // ========== МЕТОДЫ ПРОБЛЕМНОГО КОДА ==========
    @FXML
    public void runProblematicCodeScan() {
        problematicCode.runProblematicCodeScan();
    }

    @FXML
    public void showProblemStats() {
        problematicCode.showProblemStats();
    }

    @FXML
    public void exportProblemReport() {
        problematicCode.exportProblemReport();
    }

    @FXML
    public void copyProblemFunctionText() {
        problematicCode.copyProblemFunctionText();
    }

    // ========== ГЕТТЕРЫ ДЛЯ ПРОИЗВОДИТЕЛЬНОСТИ ==========
    public TableView<Scanner_Performance.PerformanceIssue> getPerformanceIssuesTable() {
        return performanceIssuesTable;
    }

    public ProgressBar getPerformanceScanProgress() {
        return performanceScanProgress;
    }

    public Label getPerformanceTotalLabel() {
        return performanceTotalLabel;
    }

    public Label getPerformanceCriticalLabel() {
        return performanceCriticalLabel;
    }

    public Label getPerformanceHighLabel() {
        return performanceHighLabel;
    }

    public Label getPerformanceMediumLabel() {
        return performanceMediumLabel;
    }

    public Label getSelectedPerformanceLabel() {
        return selectedPerformanceLabel;
    }

    public Label getPerformanceLocationLabel() {
        return performanceLocationLabel;
    }

    public TextArea getPerformanceDetailText() {
        return performanceDetailText;
    }

    public CodeArea getPerformanceFunctionCodeArea() {
        return performanceFunctionCodeArea;
    }

    public Label getPerformanceFunctionInfoLabel() {
        return performanceFunctionInfoLabel;
    }

    // ========== ГЕТТЕРЫ ДЛЯ ПРОБЛЕМНОГО КОДА ==========
    public ProgressBar getProblemScanProgress() {
        return problemScanProgress;
    }

    public Label getProblemTotalLabel() {
        return problemTotalLabel;
    }

    public Label getProblemCriticalLabel() {
        return problemCriticalLabel;
    }

    public Label getProblemHighLabel() {
        return problemHighLabel;
    }

    public Label getProblemMediumLabel() {
        return problemMediumLabel;
    }

    public Label getProblemLowLabel() {
        return problemLowLabel;
    }

    public TableView<DependencyProblematicCode.CodeProblem> getProblemIssuesTable() {
        return problemIssuesTable;
    }

    public Label getSelectedProblemLabel() {
        return selectedProblemLabel;
    }

    public Label getProblemLocationLabel() {
        return problemLocationLabel;
    }

    public TextArea getProblemDetailText() {
        return problemDetailText;
    }

    public CodeArea getProblemFunctionCodeArea() {
        return problemFunctionCodeArea;
    }

    public Label getProblemFunctionInfoLabel() {
        return problemFunctionInfoLabel;
    }

    // ========== ОСТАЛЬНЫЕ ГЕТТЕРЫ (остаются как были) ==========
    public TreeView<String> getClassTree() { return classTree; }
    public TextField getSearchField() { return searchField; }
    public Button getClearSearchButton() { return clearSearchButton; }
    public TableView<FunctionInfo> getFunctionsTable() { return functionsTable; }
    public Label getFunctionsCountLabel() { return functionsCountLabel; }
    public Label getSelectedFunctionLabel() { return selectedFunctionLabel; }
    public VBox getElementGraphContainer() { return elementGraphContainer; }
    public CodeArea getModuleCodeArea() { return moduleCodeArea; }
    public Button getBackButton() { return backButton; }
    public CodeArea getWorkbenchCodeArea() { return workbenchCodeArea; }
    public TextArea getWorkbenchLogArea() { return workbenchLogArea; }
    public Label getWorkbenchStatusLabel() { return workbenchStatusLabel; }
    public Label getWorkbenchStatsLabel() { return workbenchStatsLabel; }
    public TextField getFunctionTextSearchField() { return functionTextSearchField; }
    public Label getSearchResultsCount() { return searchResultsCount; }
    public Label getSearchTimeLabel() { return searchTimeLabel; }
    public TableView<FunctionSearchResult> getSearchResultsTable() { return searchResultsTable; }
    public CodeArea getFunctionDetailCodeArea() { return functionDetailCodeArea; }
    public TableView<Scanner_Security.SecurityIssue> getSecurityIssuesTable() { return securityIssuesTable; }
    public CodeArea getSecurityFunctionCodeArea() { return securityFunctionCodeArea; }
    public Label getSelectedVulnerabilityLabel() { return selectedVulnerabilityLabel; }
    public Label getVulnerabilityLocationLabel() { return vulnerabilityLocationLabel; }
    public Label getFunctionInfoLabel() { return functionInfoLabel; }
    public TextArea getSecurityDetailText() { return securityDetailText; }
    public ProgressBar getSecurityScanProgress() { return securityScanProgress; }
    public Label getSecurityTotalLabel() { return securityTotalLabel; }
    public Label getSecurityCriticalLabel() { return securityCriticalLabel; }
    public Label getSecurityHighLabel() { return securityHighLabel; }
    public Label getSecurityMediumLabel() { return securityMediumLabel; }
    public TextArea getMessagesArea() { return messagesArea; }
    public ProgressBar getLoadProgress() { return loadProgress; }
    public Label getLoadStatus() { return loadStatus; }
    public TextField getConfigPathField() { return configPathField; }
    public ComboBox<String> getGraphStyleComboBox() { return graphStyleComboBox; }
    public ObservableList<FunctionInfo> getFunctionsList() { return functionsList; }

    // Геттеры для полей поиска
    public TextField getCodeAnalyzerSearchField() { return codeAnalyzerSearchField; }
    public Button getCodeAnalyzerSearchPrevBtn() { return codeAnalyzerSearchPrevBtn; }
    public Button getCodeAnalyzerSearchNextBtn() { return codeAnalyzerSearchNextBtn; }
    public Label getCodeAnalyzerSearchCounter() { return codeAnalyzerSearchCounter; }

    public TextField getWorkbenchSearchField() { return workbenchSearchField; }
    public Button getWorkbenchSearchPrevBtn() { return workbenchSearchPrevBtn; }
    public Button getWorkbenchSearchNextBtn() { return workbenchSearchNextBtn; }
    public Label getWorkbenchSearchCounter() { return workbenchSearchCounter; }

    public TextField getSearchTabSearchField() { return searchTabSearchField; }
    public Button getSearchTabSearchPrevBtn() { return searchTabSearchPrevBtn; }
    public Button getSearchTabSearchNextBtn() { return searchTabSearchNextBtn; }
    public Label getSearchTabSearchCounter() { return searchTabSearchCounter; }

    public TextField getSecurityCodeSearchField() { return securityCodeSearchField; }
    public Button getSecurityCodeSearchPrevBtn() { return securityCodeSearchPrevBtn; }
    public Button getSecurityCodeSearchNextBtn() { return securityCodeSearchNextBtn; }
    public Label getSecurityCodeSearchCounter() { return securityCodeSearchCounter; }

    public TextField getPerformanceCodeSearchField() { return performanceCodeSearchField; }
    public Button getPerformanceCodeSearchPrevBtn() { return performanceCodeSearchPrevBtn; }
    public Button getPerformanceCodeSearchNextBtn() { return performanceCodeSearchNextBtn; }
    public Label getPerformanceCodeSearchCounter() { return performanceCodeSearchCounter; }

    public ElementGraphView getElementGraphView() {
        if (elementGraphView == null) {
            for (javafx.scene.Node node : elementGraphContainer.getChildren()) {
                if (node instanceof ElementGraphView) {
                    elementGraphView = (ElementGraphView) node;
                    break;
                }
            }
        }
        return elementGraphView;
    }
    public void setElementGraphView(ElementGraphView view) {
        this.elementGraphView = view;
    }

    // ========== ОСТАЛЬНЫЕ МЕТОДЫ (остаются как были) ==========
    private void initializeGraphStyleComboBox() {
        if (graphStyleComboBox == null) return;

        javafx.collections.ObservableList<String> styles = FXCollections.observableArrayList(
                "Классика", "Современный", "Минимализм"
        );
        graphStyleComboBox.setItems(styles);

        graphStyleComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && codeAnalyzer != null) {
                codeAnalyzer.applyGraphStyle(newVal);
            }
        });

        graphStyleComboBox.setValue("Классика");
    }

    @FXML public void checkSpelling() { if (spellChecker != null) spellChecker.checkSpelling(); }
    @FXML public void changeConfigDirectory() { codeAnalyzer.changeConfigDirectory(); }
    @FXML public void onSaveToDb() { codeAnalyzer.onSaveToDb(); }
    @FXML public void runParserTest() { codeAnalyzer.runParserTest(); }
    @FXML public void clearMessages() { messagesArea.clear(); }
    @FXML public void showAboutDialog() { if (codeAnalyzer != null) codeAnalyzer.showAboutDialog(); }
    @FXML public void showFeedbackDialog() { if (codeAnalyzer != null) codeAnalyzer.showFeedbackDialog(); }
    @FXML public void onSearchClick() { codeAnalyzer.onSearchClick(); }
    @FXML public void onClearSearch() { codeAnalyzer.onClearSearch(); }
    @FXML public void expandAllTree() { codeAnalyzer.expandAllTree(); }
    @FXML public void collapseAllTree() { codeAnalyzer.collapseAllTree(); }
    @FXML public void refreshTree() { codeAnalyzer.refreshTree(); }
    @FXML public void exportTree() { codeAnalyzer.exportTree(); }
    @FXML public void exportFunctionsList() { codeAnalyzer.exportFunctionsList(); }
    @FXML public void goBackInGraphHistory() { codeAnalyzer.goBackInGraphHistory(); }
    @FXML public void resetGraphZoom() { codeAnalyzer.resetGraphZoom(); }
    @FXML public void saveGraphToPdfLandscape() { codeAnalyzer.saveGraphToPdfLandscape(); }
    @FXML public void validateCode() { workbench.validateCode(); }
    @FXML public void queryToText() { workbench.queryToText(); }
    @FXML public void textToQuery() { workbench.textToQuery(); }
    @FXML public void clearValidationResults() { if (validationResultsArea != null) validationResultsArea.clear(); }
    @FXML public void format1CCode() { if (workbench != null) workbench.format1CCode(); }
    @FXML public void clearWorkbench() { workbench.clearWorkbench(); }
    @FXML public void clearWorkbenchLog() { workbench.clearWorkbenchLog(); }
    @FXML public void searchInFunctions() { functionSearch.searchInFunctions(); }
    @FXML public void clearFunctionSearch() { functionSearch.clearFunctionSearch(); }
    @FXML public void runSecurityScan() { securityScanner.runSecurityScan(); }
    @FXML public void showSecurityStats() { securityScanner.showSecurityStats(); }

    @FXML public void copyFunctionText() { securityScanner.copyFunctionText(); }


    // Добавь этот метод в класс DependencyController
    public TabPane getTabPane() {
        return tabPane;  // tabPane уже есть в FXML с fx:id="tabPane"
    }

    @FXML
    public void exportSecurityReport() {
        if (securityScanner != null) {
            securityScanner.exportSecurityReport();
        }
    }

    @FXML
    public void scanTransactions() {
        if (securityScanner != null) {
            securityScanner.scanTransactions();
        }
    }

    @FXML
    public void runErrorsScan() {
        addMessage("🐛 Запущено сканирование ошибок...");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception { Thread.sleep(1000); return null; }
        };
        task.setOnSucceeded(e -> addMessage("✅ Сканирование ошибок завершено (заглушка)"));
        task.setOnFailed(e -> addMessage("❌ Ошибка сканирования ошибок: " + task.getException().getMessage()));
        new Thread(task).start();
    }

    public void shutdown() {
        if (codeAnalyzer != null) codeAnalyzer.shutdown();
        if (workbench != null) workbench.shutdown();
        if (functionSearch != null) functionSearch.shutdown();
        if (securityScanner != null) securityScanner.shutdown();
        if (performanceScanner != null) performanceScanner.shutdown();
        if (problematicCode != null) problematicCode.shutdown();
        if (spellChecker != null) spellChecker.shutdown();

        // ОСТАНАВЛИВАЕМ ПРОВЕРКУ ОБНОВЛЕНИЙ
        UpdateChecker.stopPeriodicCheck();

        // ЗАВЕРШАЕМ ПРИЛОЖЕНИЕ
        Platform.exit();
        System.exit(0);
    }

    private void addMessage(String message) {
        if (messagesArea != null) {
            Platform.runLater(() -> {
                messagesArea.appendText(message + "\n");
                messagesArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }

    @FXML public void onStyleClassicSelected() {
        ElementGraphView graphView = getElementGraphView();
        if (graphView != null) {
            graphView.setGraphStyle(ElementGraphView.GraphStyle.CLASSIC);
            refreshCurrentGraph();
        }
    }

    @FXML public void onStyleModernSelected() {
        ElementGraphView graphView = getElementGraphView();
        if (graphView != null) {
            graphView.setGraphStyle(ElementGraphView.GraphStyle.MODERN);
            refreshCurrentGraph();
        }
    }

    @FXML public void onStyleMinimalistSelected() {
        ElementGraphView graphView = getElementGraphView();
        if (graphView != null) {
            graphView.setGraphStyle(ElementGraphView.GraphStyle.MINIMALIST);
            refreshCurrentGraph();
        }
    }

    @FXML
    public void scanUnusedFunctions() {
        if (securityScanner != null) {
            securityScanner.scanBadVariableNames();
        }
    }

    private void refreshCurrentGraph() {
        FunctionInfo currentFunction = functionsTable.getSelectionModel().getSelectedItem();
        if (currentFunction != null) {
            codeAnalyzer.buildElementGraphForFunction(currentFunction);
        } else {
            String labelText = selectedFunctionLabel.getText();
            if (labelText != null && !labelText.equals("Выберите функцию")) {
                FunctionInfo func = findFunctionByName(labelText);
                if (func != null) {
                    codeAnalyzer.buildElementGraphForFunction(func);
                }
            }
        }
    }

    private FunctionInfo findFunctionByName(String name) {
        for (FunctionInfo func : functionsTable.getItems()) {
            if (func.getName().equals(name)) {
                return func;
            }
        }
        return null;
    }


    // Добавьте этот геттер в конец класса, где остальные геттеры
    public TextField getPerformanceSearchField() {
        return performanceSearchField;
    }

    public Button getPerformanceSearchButton() {
        return performanceSearchButton;
    }

    public Button getPerformanceClearSearchButton() {
        return performanceClearSearchButton;
    }


    @FXML
    public void filterPerformanceTable() {
        if (performanceScanner != null) {
            performanceScanner.filterPerformanceTable();
        }
    }

    @FXML
    public void clearPerformanceSearch() {
        if (performanceScanner != null) {
            performanceScanner.clearPerformanceSearch();
        }
    }

    public TableView<FunctionSearchResult> getCallersTable() {
        return callersTable;
    }

    public Label getCallersCountLabel() {
        return callersCountLabel;
    }

    public Label getCallersTitleLabel() {
        return callersTitleLabel;
    }


    // Геттер для кнопки экспорта
    public Button getSecurityExportButton() {
        return securityExportButton; // имя переменной, как в FXML
    }

    // Кнопка для фоновых заданий
    public Button getSecurityBackgroundJobsButton() {
        return securityBackgroundJobsButton;
    }

    // Статус бар
    public Label getSecurityStatusLabel() {
        return securityStatusLabel;
    }

    // Кнопки для скрытия на 4й вкладке
    public Button getSecurityPerformanceButton() {
        return securityPerformanceButton;
    }

    public Button getSecurityErrorsButton() {
        return securityErrorsButton;
    }


    public Button getLoadButton() {
        return loadButton;  // нужно добавить fx:id="loadButton" в FXML
    }

    public Button getConfigPathButton() {
        return configPathButton;  // нужно добавить fx:id="configPathButton" в FXML
    }



    @FXML
    public void scanLocks() {
        if (securityScanner != null) {
            securityScanner.scanLocks();
        } else {
            addMessage("❌ Сканер не инициализирован");
        }
    }

    @FXML
    public void scanBackgroundJobs() {
        System.out.println("🔘 Кнопка 'Фоновые задания' нажата");
        if (securityScanner != null) {
            securityScanner.scanBackgroundJobs();
        } else {
            System.err.println("❌ securityScanner == null");
            addMessage("❌ Сканер не инициализирован");
        }
    }



    /**
     * Показывает окно помощи для указанной вкладки
     * @param title Заголовок окна
     * @param content Текст с описанием
     */
    private void showHelpDialog(String title, String content) {
        Platform.runLater(() -> {
            try {
                Stage helpStage = new Stage();
                helpStage.setTitle("Помощь - " + title);
                helpStage.setResizable(false);
                helpStage.setWidth(550);
                helpStage.setHeight(500);

                Stage mainStage = (Stage) classTree.getScene().getWindow();
                helpStage.initOwner(mainStage);
                helpStage.initModality(Modality.WINDOW_MODAL);

                VBox mainContainer = new VBox(0);
                mainContainer.setStyle("-fx-background-color: white;");

                // Шапка с цветной полосой (как в about)
                VBox headerBox = new VBox(10);
                headerBox.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #3498db); -fx-padding: 20 30 20 30;");

                HBox headerContent = new HBox(15);
                headerContent.setAlignment(Pos.CENTER_LEFT);

                Label helpIcon = new Label("❓");
                helpIcon.setStyle("-fx-font-size: 40px;");

                VBox titleBox = new VBox(5);
                Label titleLabel = new Label(title);
                titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

                Label subtitleLabel = new Label("Интерактивная справка");
                subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.8);");

                titleBox.getChildren().addAll(titleLabel, subtitleLabel);
                headerContent.getChildren().addAll(helpIcon, titleBox);
                headerBox.getChildren().add(headerContent);

                // Контент
                VBox contentBox = new VBox(15);
                contentBox.setPadding(new Insets(25, 30, 25, 30));
                contentBox.setStyle("-fx-background-color: #f8f9fa;");

                TextArea contentArea = new TextArea();
                contentArea.setText(content);
                contentArea.setEditable(false);
                contentArea.setWrapText(true);
                contentArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 12px; " +
                        "-fx-control-inner-background: white; " +
                        "-fx-text-fill: #333; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-radius: 6; " +
                        "-fx-border-color: #ddd; " +
                        "-fx-border-width: 1;");
                contentArea.setPrefHeight(320);

                // Кнопка закрытия
                Button closeButton = new Button("Закрыть");
                closeButton.setStyle("-fx-background-color: #3498db; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 8 30; " +
                        "-fx-background-radius: 4; " +
                        "-fx-cursor: hand;");
                closeButton.setOnAction(e -> helpStage.close());

                HBox buttonBox = new HBox();
                buttonBox.setAlignment(Pos.CENTER);
                buttonBox.getChildren().add(closeButton);

                contentBox.getChildren().addAll(contentArea, buttonBox);
                mainContainer.getChildren().addAll(headerBox, contentBox);

                Scene scene = new Scene(mainContainer);
                helpStage.setScene(scene);
                helpStage.show();

            } catch (Exception e) {
                System.err.println("❌ Ошибка создания окна помощи: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    @FXML

    public void showHelpForPerformanceTab() {
        String content = HelpContent.getPerformanceHelp();  // ← получаем контент
        showHelpDialog("Анализатор производительности", content);
    }

    @FXML
    public void showHelpForAnalyzerTab() {
        showHelpDialog("Анализатор кода", HelpContent.getAnalyzerHelp());
    }

    @FXML
    public void showHelpForWorkbenchTab() {
        showHelpDialog("Работа с модулем", HelpContent.getWorkbenchHelp());
    }

    @FXML
    public void showHelpForSearchTab() {
        showHelpDialog("Поиск по модулям", HelpContent.getSearchHelp());
    }

    @FXML
    public void showHelpForSecurityTab() {
        showHelpDialog("Сканер проблем в коде", HelpContent.getSecurityHelp());
    }


    public TextField getSearchTabCodeSearchField() { return searchTabCodeSearchField; }
    public Button getSearchTabCodeSearchPrevBtn() { return searchTabCodeSearchPrevBtn; }
    public Button getSearchTabCodeSearchNextBtn() { return searchTabCodeSearchNextBtn; }
    public Label getSearchTabCodeSearchCounter() { return searchTabCodeSearchCounter; }


    public TextField getWorkbenchCodeSearchField() { return workbenchCodeSearchField; }
    public Button getWorkbenchCodeSearchPrevBtn() { return workbenchCodeSearchPrevBtn; }
    public Button getWorkbenchCodeSearchNextBtn() { return workbenchCodeSearchNextBtn; }
    public Label getWorkbenchCodeSearchCounter() { return workbenchCodeSearchCounter; }



    @FXML
    public void onWorkbenchSearchPrev() {
        if (workbench != null) {
            workbench.searchPrevInCode();
        }
    }

    @FXML
    public void onWorkbenchSearchNext() {
        if (workbench != null) {
            workbench.searchNextInCode();
        }
    }


    @FXML
    public void onCodeAnalyzerSearchPrev() {
        if (codeAnalyzer != null) {
            codeAnalyzer.searchPrevInCode();
        }
    }

    @FXML
    public void onCodeAnalyzerSearchNext() {
        if (codeAnalyzer != null) {
            codeAnalyzer.searchNextInCode();
        }
    }

    @FXML
    public void runNestedElementsScan() {
        System.out.println("🔘 Кнопка нажата: runNestedElementsScan");
        if (performanceScanner != null) {
            performanceScanner.runNestedElementsScan();
        } else {
            System.out.println("❌ performanceScanner == null");
            addMessage("❌ Сканер производительности не инициализирован");
        }
    }



    @FXML
    public void filterSecurityTable() {
        if (securityScanner != null) {
            securityScanner.filterSecurityTable();
        }
    }

    @FXML
    public void clearSecuritySearch() {
        if (securityScanner != null) {
            securityScanner.clearSecuritySearch();
        }
    }
    public TextField getSecuritySearchField() {
        return securitySearchField;
    }
    @FXML
    public void scanLoopQueries() {
        if (securityScanner != null) {
            securityScanner.scanLoopQueries();
        }
    }


}