package com.lycurg.metavisionfor1c;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javafx.stage.Modality;
import javafx.scene.text.Text;
import java.net.*;
import java.io.*;


import javafx.scene.input.KeyCode;
import org.fxmisc.richtext.CodeArea;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TablePosition;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import com.lycurg.metavisionfor1c.ConfigTreeManager;


//# Контроллер вкладки 1: управление деревом объектов, функциями, графом зависимостей и навигацией
public class DependencyCodeAnalyzer {

    public  FunctionInfo OsnFunc = null;

    private final DependencyController mainController;
    private ElementGraphView elementGraphView;
    private NavigationHistory graphHistory;
    private final ObservableList<FunctionInfo> functionsList = FXCollections.observableArrayList();
    private TreeItem<String> originalTreeRoot;
    private MySettings settings;
    private String configDir;
    private String configXmlPath;
    private final ExecutorService executor;


    public static final String VERSION = Application_MetaVision.VERSION;

    // 🔥 НОВОЕ ПОЛЕ (если нужно)
    private ComboBox<String> graphStyleComboBox;

    private CodeSearchHelper codeAnalyzerSearch;

    private static class NavigationHistory {
        private final LinkedList<FunctionInfo> history = new LinkedList<>();
        private static final int MAX_HISTORY = 10;

        public void add(FunctionInfo function) {
            if (!history.isEmpty() && history.getLast().getFunctionId() == function.getFunctionId()) {
                return;
            }
            history.addLast(function);
            if (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }

        public void clear() {
            history.clear();
        }

        public FunctionInfo goBack() {
            if (history.size() > 1) {
                history.removeLast();
                return history.getLast();
            }
            return null;
        }
    }

    private static class ObjectInfo {
        private final String fullName;
        private final String type;
        private final int objectId;
        private final String moduleType;
        private final String moduleName;

        public ObjectInfo(String fullName, String type, int objectId, String moduleType, String moduleName) {
            this.fullName = fullName;
            this.type = type;
            this.objectId = objectId;
            this.moduleType = moduleType;
            this.moduleName = moduleName;
        }

        public String getFullName() { return fullName; }
        public String getModuleType() { return moduleType; }
        public String getModuleName() { return moduleName; }
    }

    public DependencyCodeAnalyzer(DependencyController controller) {
        this.mainController = controller;
        this.executor = ExecutorServiceSingleton.getInstance();
    }

    public void initialize() {
        settings = new MySettings();
        configDir = settings.get("config_dir", "");
        configXmlPath = configDir + "/Configuration.xml";
        mainController.getConfigPathField().setText(configDir);

        elementGraphView = new ElementGraphView();
        mainController.getElementGraphContainer().getChildren().add(elementGraphView);

        elementGraphView.setFunctionClickListener((functionId, functionName) -> {
            onFunctionSelectedInGraph(functionId, functionName);
        });


        elementGraphView.setLineNavigationListener(lineNumber -> {
            Platform.runLater(() -> {
                CodeArea codeArea = mainController.getModuleCodeArea();
                // Скроллим к нужной строке
                codeArea.showParagraphAtTop(lineNumber - 1);
                // Выделяем строку
                int lineStart = codeArea.getAbsolutePosition(lineNumber - 1, 0);
                int lineEnd = codeArea.getAbsolutePosition(lineNumber - 1,
                        codeArea.getParagraph(lineNumber - 1).length());
                codeArea.selectRange(lineStart, lineEnd);
            });
        });
        graphHistory = new NavigationHistory();
        setupFunctionsTable();
        setupTreeSelectionListener();
        loadTreeFromDb();

        setupTreeCopyHandler();

        setupCodeSearch();
    }


    private void setupTreeSelectionListener() {
        mainController.getClassTree().getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        onTreeNodeSelected(newSelection);
                    }
                }
        );
    }

    private void setupCodeSearch() {
        TextField searchField = mainController.getCodeAnalyzerSearchField();
        Button prevBtn = mainController.getCodeAnalyzerSearchPrevBtn();
        Button nextBtn = mainController.getCodeAnalyzerSearchNextBtn();
        Label counter = mainController.getCodeAnalyzerSearchCounter();
        CodeArea codeArea = mainController.getModuleCodeArea();

        if (searchField != null && codeArea != null) {
            codeAnalyzerSearch = new CodeSearchHelper(codeArea, searchField, prevBtn, nextBtn, counter);
        }
    }

    public void searchPrevInCode() {
        if (codeAnalyzerSearch != null) codeAnalyzerSearch.navigatePrev();
    }

    public void searchNextInCode() {
        if (codeAnalyzerSearch != null) codeAnalyzerSearch.navigateNext();
    }

    // 🔥 НОВЫЙ МЕТОД: Получить ComboBox из контроллера
    private ComboBox<String> getGraphStyleComboBox() {
        if (graphStyleComboBox == null && mainController != null) {
            try {
                graphStyleComboBox = mainController.getGraphStyleComboBox();
            } catch (Exception e) {
                javafx.scene.Node node = mainController.getClassTree().getScene().lookup("#graphStyleComboBox");
                if (node instanceof ComboBox) {
                    graphStyleComboBox = (ComboBox<String>) node;
                }
            }
        }
        return graphStyleComboBox;
    }

    // 🔥 НОВЫЙ МЕТОД: Применить стиль графа
    public void applyGraphStyle(String styleName) {
        if (elementGraphView == null) {
            elementGraphView = mainController.getElementGraphView();
        }

        if (elementGraphView != null) {
            switch (styleName) {
                case "Классика":
                    elementGraphView.setGraphStyle(ElementGraphView.GraphStyle.CLASSIC);
                    break;
                case "Современный":
                    elementGraphView.setGraphStyle(ElementGraphView.GraphStyle.MODERN);
                    break;
                case "Минимализм":
                    elementGraphView.setGraphStyle(ElementGraphView.GraphStyle.MINIMALIST);
                    break;
                default:
                    elementGraphView.setGraphStyle(ElementGraphView.GraphStyle.CLASSIC);
            }

            refreshCurrentGraph();
        }
    }

    // 🔥 МЕТОД ОБНОВЛЕНИЯ ГРАФА
    private void refreshCurrentGraph() {
        FunctionInfo currentFunction = mainController.getFunctionsTable().getSelectionModel().getSelectedItem();
        if (currentFunction != null) {
            buildElementGraphForFunction(currentFunction);
        }
    }


    private void setupTreeCopyHandler() {
        mainController.getClassTree().setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                TreeItem<String> selectedItem = mainController.getClassTree().getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    String textToCopy = selectedItem.getValue();

                    StringSelection selection = new StringSelection(textToCopy);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(selection, selection);

                    event.consume();
                }
            }
        });
    }

    public void loadNastroek() {
        try {
            final SplitPane mainSplitPaneFinal = findMainSplitPane();
            if (mainSplitPaneFinal != null && mainSplitPaneFinal.getDividers().size() >= 2) {
                String pos1Str = settings.get("splitpane.divider1.position", "0.25");
                String pos2Str = settings.get("splitpane.divider2.position", "0.5");
                final double pos1 = parseDoubleWithDefault(pos1Str, 0.25);
                final double pos2 = parseDoubleWithDefault(pos2Str, 0.5);

                Platform.runLater(() -> {
                    mainSplitPaneFinal.setDividerPositions(pos1, pos2);
                    mainSplitPaneFinal.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> saveNastroek());
                    mainSplitPaneFinal.getDividers().get(1).positionProperty().addListener((obs, oldVal, newVal) -> saveNastroek());
                });
            }

            final SplitPane verticalSplitPaneFinal = findVerticalSplitPane();
            if (verticalSplitPaneFinal != null && verticalSplitPaneFinal.getDividers().size() > 0) {
                String verticalPosStr = settings.get("vertical.splitpane.position", "0.7");
                final double position = parseDoubleWithDefault(verticalPosStr, 0.7);
                final double adjustedPosition = Math.max(0.1, Math.min(0.9, position));

                Platform.runLater(() -> {
                    verticalSplitPaneFinal.setDividerPositions(adjustedPosition);
                    verticalSplitPaneFinal.getDividers().get(0).positionProperty().addListener((obs, oldVal, newVal) -> saveNastroek());
                });
            }

            String savedStyle = settings.get("graph.style", "Классика");
            Platform.runLater(() -> {
                ComboBox<String> comboBox = getGraphStyleComboBox();
                if (comboBox != null) {
                    comboBox.setValue(savedStyle);
                    applyGraphStyle(savedStyle);
                }
            });

        } catch (Exception e) {
            System.err.println("Ошибка загрузки настроек: " + e.getMessage());
        }
    }

    public void saveNastroek() {
        try {
            SplitPane mainSplitPane = findMainSplitPane();
            if (mainSplitPane != null && mainSplitPane.getDividers().size() >= 2) {
                double[] positions = mainSplitPane.getDividerPositions();
                if (positions.length >= 2) {
                    settings.set("splitpane.divider1.position", String.valueOf(positions[0]));
                    settings.set("splitpane.divider2.position", String.valueOf(positions[1]));
                }
            }

            SplitPane verticalSplitPane = findVerticalSplitPane();
            if (verticalSplitPane != null && verticalSplitPane.getDividers().size() > 0) {
                double[] verticalPositions = verticalSplitPane.getDividerPositions();
                if (verticalPositions.length > 0) {
                    settings.set("vertical.splitpane.position", String.valueOf(verticalPositions[0]));
                }
            }

            ComboBox<String> comboBox = getGraphStyleComboBox();
            if (comboBox != null && comboBox.getValue() != null) {
                settings.set("graph.style", comboBox.getValue());
            }

        } catch (Exception e) {
            System.err.println("Ошибка сохранения настроек: " + e.getMessage());
        }
    }

    private SplitPane findMainSplitPane() {
        try {
            javafx.scene.Node node = mainController.getClassTree();
            while (node != null) {
                if (node instanceof SplitPane) {
                    SplitPane pane = (SplitPane) node;
                    if (pane.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) {
                        return pane;
                    }
                }
                node = node.getParent();
            }
        } catch (Exception e) {}
        return null;
    }

    private SplitPane findVerticalSplitPane() {
        try {
            javafx.scene.Node node = mainController.getElementGraphContainer();
            while (node != null) {
                if (node instanceof SplitPane) {
                    SplitPane pane = (SplitPane) node;
                    if (pane.getOrientation() == javafx.geometry.Orientation.VERTICAL) {
                        return pane;
                    }
                }
                node = node.getParent();
            }
        } catch (Exception e) {}
        return null;
    }

    private double parseDoubleWithDefault(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public void changeConfigDirectory() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Выберите папку конфигурации 1С");
        File currentDir = new File(configDir);
        if (currentDir.exists() && currentDir.isDirectory()) {
            dirChooser.setInitialDirectory(currentDir);
        } else if (currentDir.getParentFile() != null && currentDir.getParentFile().exists()) {
            dirChooser.setInitialDirectory(currentDir.getParentFile());
        }

        File selectedDir = dirChooser.showDialog(mainController.getClassTree().getScene().getWindow());
        if (selectedDir != null) {
            configDir = selectedDir.getAbsolutePath();
            configXmlPath = configDir + "/Configuration.xml";

            File configFile = new File(configXmlPath);
            if (!configFile.exists()) {
                showAlert("Ошибка", "Configuration.xml не найден:\n" + configXmlPath, Alert.AlertType.ERROR);
                return;
            }

            settings.set("config_dir", configDir);
            mainController.getConfigPathField().setText(configDir);
            //  showAlert("Успех", "Путь сохранён:\n" + configDir, Alert.AlertType.INFORMATION);
        }
    }

    public void onSaveToDb() {
        // БЛОКИРУЕМ ВСЁ
        TabPane tabPane = mainController.getTabPane();
        Button loadButton = mainController.getLoadButton();
        Button configPathButton = mainController.getConfigPathButton();

        tabPane.setDisable(true);
        loadButton.setDisable(true);
        loadButton.setText("⏳ ЗАГРУЗКА...");
        configPathButton.setDisable(true);

        mainController.getLoadProgress().setVisible(true);
        mainController.getLoadProgress().setProgress(0);
        mainController.getLoadStatus().setVisible(true);
        mainController.getLoadStatus().setText("Подготовка к загрузке...");
        addMessage("=== НАЧАЛО ЗАГРУЗКИ КОНФИГУРАЦИИ ===");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                long startTime = System.currentTimeMillis();
                try {
                    File dbFile = new File(DBPathHelper.getDbPath());
                    if (dbFile.exists()) {
                        Platform.runLater(() -> addMessage("🗑️ Удаление старой базы данных..."));
                        dbFile.delete();
                    }

                    UnifiedDataLoader dataLoader = new UnifiedDataLoader(configDir);

                    dataLoader.setProgressCallback(progress -> {
                        Platform.runLater(() -> {
                            mainController.getLoadProgress().setProgress(progress);
                            mainController.getLoadStatus().setText(
                                    String.format("Загрузка: %.0f%%", progress * 100)
                            );
                        });
                    });

                    dataLoader.setMessageConsumer(msg -> Platform.runLater(() -> addMessage(msg)));
                    dataLoader.loadAllData();

                    long endTime = System.currentTimeMillis();
                    long duration = (endTime - startTime) / (1000 * 60);
                    Platform.runLater(() -> {
                        addMessage("✅ ЗАГРУЗКА ЗАВЕРШЕНА! Время: " + duration + " мин");
                        mainController.getLoadStatus().setText("");
                        mainController.getLoadProgress().setProgress(1.0);
                        mainController.getLoadProgress().setVisible(false);
                        addMessage("✅ Загрузка конфигурации успешно завершена!");
                        loadTreeFromDb();
                        addMessage("✅ Дерево обновлено");

                        // РАЗБЛОКИРУЕМ ВКЛАДКИ
                        tabPane.setDisable(false);
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        addMessage("❌ ОШИБКА: " + e.getMessage());
                        addMessage("=== ЗАГРУЗКА ПРЕРВАНА ===");
                        mainController.getLoadProgress().setVisible(false);

                        // РАЗБЛОКИРУЕМ ВКЛАДКИ ПРИ ОШИБКЕ
                        tabPane.setDisable(false);
                    });
                    throw e;
                }
                return null;
            }
        };

// РАЗБЛОКИРОВКА ПОСЛЕ УСПЕХА
        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                tabPane.setDisable(false);
                loadButton.setDisable(false);
                loadButton.setText("💿 Загрузка данных конфигурации");
                configPathButton.setDisable(false);
                mainController.getLoadProgress().setVisible(false);
            });
        });

// РАЗБЛОКИРОВКА ПРИ ОШИБКЕ
        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                tabPane.setDisable(false);
                loadButton.setDisable(false);
                loadButton.setText("💿 Загрузка данных конфигурации");
                configPathButton.setDisable(false);
                mainController.getLoadProgress().setVisible(false);
                addMessage("❌ Ошибка: " + task.getException().getMessage());
            });
        });

        executor.submit(task);
    }


    public void showAboutDialog() {
        Platform.runLater(() -> {
            try {
                Stage aboutStage = new Stage();
                aboutStage.setTitle("О программе");
                aboutStage.setResizable(false);
                Stage mainStage = (Stage) mainController.getClassTree().getScene().getWindow();
                aboutStage.initOwner(mainStage);
                aboutStage.initModality(Modality.WINDOW_MODAL);

                VBox mainContainer = new VBox(0);
                mainContainer.setStyle("-fx-background-color: white;");

                // Шапка с цветной полосой
                VBox headerBox = new VBox(10);
                headerBox.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #3498db); -fx-padding: 25 30 20 30;");

                HBox headerContent = new HBox(20);
                headerContent.setAlignment(Pos.CENTER_LEFT);

                // Иконка
                ImageView iconView = null;
                try {
                    Image icon = new Image(getClass().getResourceAsStream("/icons/log_meta.png"));
                    iconView = new ImageView(icon);
                    iconView.setFitWidth(60);
                    iconView.setFitHeight(60);
                    iconView.setPreserveRatio(true);
                } catch (Exception e) {}

                if (iconView != null) {
                    headerContent.getChildren().add(iconView);
                }

                VBox titleBox = new VBox(5);
                Label titleLabel = new Label("MetaVision for 1C");
                titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");

                Label versionLabel = new Label("Версия: " + VERSION);
                versionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.8);");

                titleBox.getChildren().addAll(titleLabel, versionLabel);
                headerContent.getChildren().add(titleBox);
                headerBox.getChildren().add(headerContent);

                // Табы
                TabPane tabPane = new TabPane();
                tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
                tabPane.setStyle("-fx-padding: 0;");




                
// === ТАБ 1: О программе ===
                Tab aboutTab = new Tab("📋 О программе");
                VBox aboutContent = new VBox(15);
                aboutContent.setPadding(new Insets(20));
                aboutContent.setStyle("-fx-background-color: #f8f9fa;");

// Описание - используем Text с переносом
                Text descText = new Text("Профессиональный инструмент для анализа, аудита и визуализации конфигураций 1С:Предприятие.");
                descText.setWrappingWidth(380);
                descText.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-fill: #333;");

                Label authorLabel = new Label("Автор: Хорошулин Андрей Викторович");
                authorLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #555;");

                Label emailLabel = new Label("Email: lycurgussoftware@gmail.com");
                emailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #555;");

// Ссылка на лицензию
                Hyperlink licenseLink = new Hyperlink("Лицензионное соглашение");
                licenseLink.setStyle("-fx-font-size: 13px; -fx-text-fill: #3498db; -fx-border-color: transparent;");
                licenseLink.setCursor(javafx.scene.Cursor.HAND);
                licenseLink.setOnAction(e -> {
                    new Thread(() -> {
                        try {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://lycurg.com/privacy/MetaVision/%D0%9B%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D0%BE%D0%BD%D0%BD%D0%BE%D0%B5%20%D1%81%D0%BE%D0%B3%D0%BB%D0%B0%D1%88%D0%B5%D0%BD%D0%B8%D0%B5%20MetaVision%20for%201C.pdf"));
                        } catch (Exception ex) {
                            Platform.runLater(() -> addMessage("❌ Не удалось открыть лицензию: " + ex.getMessage()));
                        }
                    }).start();
                });

// Ссылка на сайт
                Hyperlink siteLink = new Hyperlink("https://lycurg.com/");
                siteLink.setStyle("-fx-font-size: 13px; -fx-text-fill: #3498db; -fx-border-color: transparent;");
                siteLink.setCursor(javafx.scene.Cursor.HAND);
                siteLink.setOnAction(e -> {
                    new Thread(() -> {
                        try {
                            java.awt.Desktop.getDesktop().browse(new java.net.URI("https://lycurg.com/"));
                        } catch (Exception ex) {
                            Platform.runLater(() -> addMessage("❌ Не удалось открыть сайт: " + ex.getMessage()));
                        }
                    }).start();
                });

                Label yearLabel = new Label("© 2025-2026, Хорошулин Андрей Викторович");
                yearLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999; -fx-padding: 15 0 0 0;");

                aboutContent.getChildren().addAll(
                        descText,
                        authorLabel,
                        emailLabel,
                        licenseLink,
                        siteLink,
                        yearLabel
                );
                aboutTab.setContent(aboutContent);






                // === ТАБ 2: Возможности ===
                Tab featuresTab = new Tab("⚡ Возможности");
                VBox featuresContent = new VBox(10);
                featuresContent.setPadding(new Insets(20));
                featuresContent.setStyle("-fx-background-color: #f8f9fa;");

                String[] features = {
                        "📊 Дерево метаданных с навигацией по функциям",
                        "🕸️ Визуализация графов вызовов (3 стиля)",
                        "🔍 Полнотекстовый поиск по всем модулям",
                        "🔒 Сканер безопасности (RCE, SSRF, COM, инъекции)",
                        "⚡ Анализатор производительности (запросы в циклах)",
                        "🛠️ Инструменты для работы с кодом (выравнивание, валидация)",
                        "📋 Экспорт таблиц в CSV и графов в PDF",
                        "🔎 Поиск внутри любого модуля с навигацией"
                };

                for (String f : features) {
                    Label l = new Label(f);
                    l.setStyle("-fx-font-size: 12px; -fx-text-fill: #333; -fx-padding: 3;");
                    featuresContent.getChildren().add(l);
                }

                featuresTab.setContent(featuresContent);

                // === ТАБ 3: Быстрый старт ===
                Tab startTab = new Tab("🚀 Быстрый старт");
                VBox startContent = new VBox(10);
                startContent.setPadding(new Insets(20));
                startContent.setStyle("-fx-background-color: #f8f9fa;");

                String[] steps = {
                        "1. Нажмите «Выбрать папку» и укажите путь к конфигурации 1С",
                        "2. Нажмите «Загрузка данных конфигурации» и дождитесь завершения",
                        "3. Выберите объект в дереве слева",
                        "4. Выберите функцию в таблице",
                        "5. Анализируйте граф и код справа"
                };

                for (String s : steps) {
                    Label l = new Label(s);
                    l.setStyle("-fx-font-size: 12px; -fx-text-fill: #333; -fx-padding: 3;");
                    startContent.getChildren().add(l);
                }

                startTab.setContent(startContent);

                tabPane.getTabs().addAll(aboutTab, featuresTab, startTab);

                // Кнопка закрытия
                Button closeButton = new Button("Закрыть");
                closeButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 25; -fx-background-radius: 4; -fx-cursor: hand;");
                closeButton.setOnAction(e -> aboutStage.close());

                VBox buttonBox = new VBox();
                buttonBox.setAlignment(Pos.CENTER);
                buttonBox.setPadding(new Insets(15, 0, 20, 0));
                buttonBox.getChildren().add(closeButton);

                mainContainer.getChildren().addAll(headerBox, tabPane, buttonBox);

                Scene scene = new Scene(mainContainer, 500, 450);
                aboutStage.setScene(scene);
                aboutStage.show();

            } catch (Exception e) {
                System.err.println("❌ Ошибка создания диалога: " + e.getMessage());
            }
        });
    }



    public void showFeedbackDialog() {
        Platform.runLater(() -> {
            try {
                final Stage feedbackStage = new Stage();
                feedbackStage.setTitle("Обратная связь");
                feedbackStage.setResizable(false);
                Stage mainStage = (Stage) mainController.getClassTree().getScene().getWindow();
                feedbackStage.initOwner(mainStage);
                feedbackStage.initModality(Modality.WINDOW_MODAL);

                VBox mainContainer = new VBox(0);
                mainContainer.setStyle("-fx-background-color: white;");

                // Шапка с цветной полосой (как в about)
                VBox headerBox = new VBox(10);
                headerBox.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #3498db); -fx-padding: 25 30 20 30;");

                HBox headerContent = new HBox(20);
                headerContent.setAlignment(Pos.CENTER_LEFT);

                // Иконка
                ImageView iconView = null;
                try {
                    Image icon = new Image(getClass().getResourceAsStream("/icons/log_meta.png"));
                    iconView = new ImageView(icon);
                    iconView.setFitWidth(50);
                    iconView.setFitHeight(50);
                    iconView.setPreserveRatio(true);
                } catch (Exception e) {}

                if (iconView != null) {
                    headerContent.getChildren().add(iconView);
                }

                VBox titleBox = new VBox(5);
                Label titleLabel = new Label("Обратная связь");
                titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");

                Label subtitleLabel = new Label("Отправьте идеи, предложения или сообщите об ошибке");
                subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.8);");

                titleBox.getChildren().addAll(titleLabel, subtitleLabel);
                headerContent.getChildren().add(titleBox);
                headerBox.getChildren().add(headerContent);

                // Основной контент
                VBox contentBox = new VBox(15);
                contentBox.setPadding(new Insets(25, 30, 20, 30));
                contentBox.setStyle("-fx-background-color: #f8f9fa;");

                // Инфо-блок (ограничения)
                VBox infoBox = new VBox(5);
                infoBox.setStyle("-fx-background-color: #fff3cd; -fx-background-radius: 6; -fx-padding: 10; -fx-border-color: #ffeeba; -fx-border-radius: 6;");

                Label infoTitle = new Label("ℹ️ Информация");
                infoTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #856404;");

                Label limitLabel = new Label("• Максимальная длина сообщения: 5000 символов\n• Не чаще 1 сообщения в 5 минут");
                limitLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #856404;");

                infoBox.getChildren().addAll(infoTitle, limitLabel);

                // Email поле
                VBox emailBox = new VBox(5);
                Label emailLabel = new Label("Ваш email для ответа (не обязательно)");
                emailLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #333;");

                TextField emailField = new TextField();
                emailField.setPromptText("name@example.com");
                emailField.setStyle("-fx-font-size: 13px; -fx-padding: 10; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #ddd; -fx-border-width: 1;");
                emailField.setMaxWidth(Double.MAX_VALUE);

                emailBox.getChildren().addAll(emailLabel, emailField);

                // Сообщение
                VBox messageBox = new VBox(5);
                Label messageLabel = new Label("Текст сообщения *");
                messageLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #333;");

                TextArea messageArea = new TextArea();
                messageArea.setPromptText("Опишите ваши идеи, предложения или найденные ошибки...");
                messageArea.setWrapText(true);
                messageArea.setStyle("-fx-font-size: 13px; -fx-control-inner-background: white; -fx-background-radius: 4; -fx-border-radius: 4; -fx-border-color: #ddd; -fx-border-width: 1;");
                messageArea.setPrefRowCount(6);
                messageArea.setMaxHeight(150);
                messageArea.setMaxWidth(Double.MAX_VALUE);

                // Счетчик символов
                Label charCounter = new Label("0 / 5000 символов");
                charCounter.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
                messageArea.textProperty().addListener((obs, oldVal, newVal) -> {
                    int len = newVal != null ? newVal.length() : 0;
                    charCounter.setText(len + " / 5000 символов");
                    if (len > 5000) {
                        charCounter.setStyle("-fx-font-size: 11px; -fx-text-fill: #e74c3c;");
                    } else {
                        charCounter.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
                    }
                });

                messageBox.getChildren().addAll(messageLabel, messageArea, charCounter);

                // Результат
                Label resultLabel = new Label();
                resultLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                resultLabel.setVisible(false);
                resultLabel.setWrapText(true);

                // Кнопки
                HBox buttonBox = new HBox(15);
                buttonBox.setAlignment(Pos.CENTER);
                buttonBox.setPadding(new Insets(15, 0, 0, 0));

                Button sendButton = new Button("📤 Отправить");
                sendButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30; -fx-background-radius: 4; -fx-cursor: hand;");

                Button cancelButton = new Button("✖️ Отмена");
                cancelButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30; -fx-background-radius: 4; -fx-cursor: hand;");
                cancelButton.setOnAction(e -> feedbackStage.close());

                buttonBox.getChildren().addAll(sendButton, cancelButton);

                contentBox.getChildren().addAll(infoBox, emailBox, messageBox, resultLabel, buttonBox);
                mainContainer.getChildren().addAll(headerBox, contentBox);

                Scene scene = new Scene(mainContainer, 480, 550);
                feedbackStage.setScene(scene);
                feedbackStage.show();

                // ========== ВСЯ ЛОГИКА ОТПРАВКИ (без изменений) ==========
                final int MAX_MESSAGE_LENGTH = 5000;
                final long MIN_DELAY_BETWEEN_MESSAGES_MS = 5 * 60 * 1000;
                final String LAST_MESSAGE_TIME_KEY = "last_feedback_time";

                sendButton.setOnAction(e -> {
                    String email = emailField.getText().trim();
                    String message = messageArea.getText().trim();
                    String machineId = MachineIdGenerator.getMachineId();
                    String version = VERSION;
                    String phpScriptUrl = "https://lycurg.com/PROJ/Site/add_message_to_MetaVisionFor1C.php";

                    if (message.isEmpty()) {
                        resultLabel.setText("✗ Поле сообщения не может быть пустым");
                        resultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                        resultLabel.setVisible(true);
                        return;
                    }

                    if (message.length() > MAX_MESSAGE_LENGTH) {
                        resultLabel.setText("✗ Сообщение слишком длинное (макс. " + MAX_MESSAGE_LENGTH + " символов)");
                        resultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                        resultLabel.setVisible(true);
                        return;
                    }

                    long lastMessageTime = 0;
                    try {
                        String savedTime = settings.get(LAST_MESSAGE_TIME_KEY, "0");
                        lastMessageTime = Long.parseLong(savedTime);
                    } catch (NumberFormatException ex) {
                        lastMessageTime = 0;
                    }

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastMessageTime < MIN_DELAY_BETWEEN_MESSAGES_MS) {
                        long remainingSeconds = (MIN_DELAY_BETWEEN_MESSAGES_MS - (currentTime - lastMessageTime)) / 1000;
                        long minutes = remainingSeconds / 60;
                        long seconds = remainingSeconds % 60;
                        resultLabel.setText("✗ Подождите " + minutes + " мин " + seconds + " сек перед следующей отправкой");
                        resultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                        resultLabel.setVisible(true);
                        return;
                    }

                    if (!email.isEmpty() && !isValidEmail(email)) {
                        resultLabel.setText("✗ Неверный формат email");
                        resultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                        resultLabel.setVisible(true);
                        return;
                    }

                    sendButton.setText("⏳ Отправка...");
                    sendButton.setDisable(true);
                    cancelButton.setDisable(true);
                    resultLabel.setVisible(false);

                    Task<String> sendTask = new Task<>() {
                        @Override
                        protected String call() throws Exception {
                            try {
                                String encodedEmail = URLEncoder.encode(email, "UTF-8");
                                String encodedMessage = URLEncoder.encode(message, "UTF-8");
                                String encodedMachineId = URLEncoder.encode(machineId, "UTF-8");
                                String encodedVersion = URLEncoder.encode(version, "UTF-8");

                                String fullUrl = phpScriptUrl +
                                        "?email=" + encodedEmail +
                                        "&message=" + encodedMessage +
                                        "&machine_id=" + encodedMachineId +
                                        "&version=" + encodedVersion;

                                URL url = new URL(fullUrl);
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("GET");
                                conn.setConnectTimeout(10000);
                                conn.setReadTimeout(10000);

                                int responseCode = conn.getResponseCode();

                                if (responseCode == 200) {
                                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                    String response = in.readLine();
                                    in.close();
                                    return response != null ? response : "no_response";
                                } else {
                                    return "http_error_" + responseCode;
                                }
                            } catch (Exception ex) {
                                return "exception: " + ex.getMessage();
                            }
                        }
                    };

                    sendTask.setOnSucceeded(evt -> {
                        String result = sendTask.getValue();
                        sendButton.setText("📤 Отправить");
                        sendButton.setDisable(false);
                        cancelButton.setDisable(false);

                        if ("success".equals(result)) {
                            settings.set(LAST_MESSAGE_TIME_KEY, String.valueOf(System.currentTimeMillis()));
                            resultLabel.setText("✓ Сообщение отправлено! Спасибо за обратную связь.");
                            resultLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                            resultLabel.setVisible(true);
                            emailField.clear();
                            messageArea.clear();
                            PauseTransition delay = new PauseTransition(Duration.seconds(2));
                            delay.setOnFinished(finishEvent -> feedbackStage.close());
                            delay.play();
                        } else {
                            resultLabel.setText("✗ Ошибка: " + result);
                            resultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                            resultLabel.setVisible(true);
                        }
                    });

                    sendTask.setOnFailed(evt -> {
                        sendButton.setText("📤 Отправить");
                        sendButton.setDisable(false);
                        cancelButton.setDisable(false);
                        resultLabel.setText("✗ Ошибка отправки");
                        resultLabel.setStyle("-fx-text-fill: #c0392b; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 10;");
                        resultLabel.setVisible(true);
                    });

                    new Thread(sendTask).start();
                });

            } catch (Exception e) {
                System.err.println("❌ Ошибка создания диалога обратной связи: " + e.getMessage());
            }
        });
    }



    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return true;
        }

        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email.matches(emailRegex);
    }

    public void runParserTest() {
        try {
            addMessage("🧪 Запуск теста парсера...");
            String testFunction = "&НаСервере\nПроцедура ТестоваяФункция(Параметр)\n    Запрос = Новый Запрос;\n    Запрос.Текст = \"SELECT * FROM Документы\";\n    \n    Для Каждого Строка Из Запрос.Выполнить().Выгрузить() Цикл\n        Сообщение = Строка.Получить(\"Наименование\");\n        ОтобразитьСообщение(Сообщение);\n    КонецЦикла;\nКонецПроцедуры";

            CodeAnalyzer.CodeStructureDetailed result = CodeAnalyzer.analyzeFunctionStructure(testFunction, "ТестоваяФункция");
            mainController.getMessagesArea().appendText("=== РЕЗУЛЬТАТЫ ПАРСЕРА ===\n");
            mainController.getMessagesArea().appendText("Найдено элементов: " + result.elements.size() + "\n");

            for (CodeElement element : result.elements) {
                mainController.getMessagesArea().appendText(element.type + "." + element.subtype + " → Владелец: " + element.ownerName + " (строки " + element.startLine + "-" + element.endLine + ")\n");
            }
            mainController.getMessagesArea().appendText("=== АНАЛИЗ ЗАВЕРШЕН ===\n\n");
        } catch (Exception e) {
            mainController.getMessagesArea().appendText("❌ Ошибка парсера: " + e.getMessage() + "\n");
        }
    }

    public void expandAllTree() {
        TreeItem<String> root = mainController.getClassTree().getRoot();
        if (root != null) {
            setExpanded(root, true);
            addMessage("✅ Дерево развернуто");
        }
    }

    public void collapseAllTree() {
        TreeItem<String> root = mainController.getClassTree().getRoot();
        if (root != null) {
            setExpanded(root, false);
            addMessage("✅ Дерево свернуто");
        }
    }

    public void refreshTree() {
        loadTreeFromDb();
        addMessage("🔄 Дерево обновлено");
    }

    public void exportTree() {
        addMessage("📤 Экспорт дерева...");
    }

    public void onSearchClick() {
        String query = mainController.getSearchField().getText().trim().toLowerCase();
        if (query.isEmpty()) {
            onClearSearch();
            return;
        }
        if (originalTreeRoot == null) {
            originalTreeRoot = mainController.getClassTree().getRoot();
        }
        TreeItem<String> filteredRoot = filterTree(originalTreeRoot, query);
        if (filteredRoot != null && !filteredRoot.getChildren().isEmpty()) {
            mainController.getClassTree().setRoot(filteredRoot);
            expandAll(filteredRoot);
        }
    }

    public void onClearSearch() {
        mainController.getSearchField().clear();
        if (originalTreeRoot != null) {
            mainController.getClassTree().setRoot(originalTreeRoot);
            originalTreeRoot = null;
        } else {
            loadTreeFromDb();
        }
    }



    public void goBackInGraphHistory() {
        graphHistory.history.removeLast();
        if (!graphHistory.history.isEmpty()) {
            FunctionInfo previousFunction = graphHistory.history.getLast();
            mainController.getSelectedFunctionLabel().setText(previousFunction.getName() + " (" + previousFunction.getType() + ") ←");
            addMessage("↩️ Возврат к предыдущей функции: " + previousFunction.getName());
            updateBackButton();
            buildElementGraphForFunction(previousFunction);
            loadModuleCodeForFunction(previousFunction);
            OsnFunc = previousFunction;
        } else {
            updateBackButton();
            onFunctionSelected(OsnFunc);
        }
    }


    public void resetGraphZoom() {
        elementGraphView.resetZoom();
        addMessage("🎯 Масштаб графа сброшен");
    }

    public void exportFunctionsList() {
        if (functionsList.isEmpty()) {
            addMessage("⚠️ Нет функций для экспорта");
            return;
        }
        addMessage("📤 Экспорт списка функций...");
    }

    private void createPdfDocument(BufferedImage graphImage, String filePath, String functionName) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDFont russianFont = loadRussianFont(document);

                contentStream.beginText();
                contentStream.setFont(russianFont, 14);
                contentStream.newLineAtOffset(50, 750);
                contentStream.showText("Граф функции: " + functionName.replace("_", " "));
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(russianFont, 10);
                contentStream.newLineAtOffset(50, 730);
                contentStream.showText("Создано: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                contentStream.endText();

                float margin = 50;
                float pageWidth = PDRectangle.A4.getWidth();
                float pageHeight = PDRectangle.A4.getHeight();
                float maxImageWidth = pageWidth - (2 * margin);
                float maxImageHeight = pageHeight - 200;

                float imageAspectRatio = (float) graphImage.getWidth() / graphImage.getHeight();
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
                float imageY = pageHeight - 150 - imageHeight;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(graphImage, "PNG", baos);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, baos.toByteArray(), "graph");
                contentStream.drawImage(pdImage, imageX, imageY, imageWidth, imageHeight);
            }

            document.save(filePath);
        }
    }

    private PDFont loadRussianFont(PDDocument document) throws IOException {
        InputStream fontStream = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
        if (fontStream != null) {
            try {
                return PDType0Font.load(document, fontStream);
            } catch (IOException e) {
            } finally {
                try { if (fontStream != null) fontStream.close(); } catch (IOException ioEx) {}
            }
        }
        return PDType1Font.HELVETICA;
    }

    public void saveGraphToPdfLandscape() {
        if (mainController.getElementGraphContainer() == null) {
            addMessage("❌ Ошибка: контейнер графа не инициализирован");
            return;
        }

        ElementGraphView graphView = null;
        for (javafx.scene.Node node : mainController.getElementGraphContainer().getChildren()) {
            if (node instanceof ElementGraphView) {
                graphView = (ElementGraphView) node;
                break;
            }
        }

        if (graphView == null) {
            addMessage("❌ Ошибка: граф не найден в контейнере");
            return;
        }

        String currentFunctionName = mainController.getSelectedFunctionLabel().getText();
        if (currentFunctionName == null || currentFunctionName.isEmpty() || currentFunctionName.equals("Выберите функцию")) {
            currentFunctionName = "Граф_без_названия";
        } else {
            currentFunctionName = currentFunctionName.replace("(", "_").replace(")", "_").replace(" ", "_").replace(":", "_").substring(0, Math.min(currentFunctionName.length(), 50));
        }

        WritableImage snapshot;
        try {
            String originalStyle = graphView.getStyle();
            graphView.setStyle(originalStyle + "; -fx-background-color: white !important;");
            graphView.snapshot(new SnapshotParameters(), null);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.WHITE);
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

        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
        if (bufferedImage == null) {
            addMessage("❌ Ошибка конвертации изображения");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить граф в PDF (альбомный)");
        fileChooser.setInitialFileName(currentFunctionName + "_граф_альбомный.pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File file = fileChooser.showSaveDialog(mainController.getElementGraphContainer().getScene().getWindow());
        if (file == null) {
            addMessage("⚠️ Сохранение отменено");
            return;
        }

        final BufferedImage finalImage = bufferedImage;
        final String finalFileName = file.getAbsolutePath();
        final String finalFunctionName = currentFunctionName;

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> addMessage("🖨️ Создание альбомного PDF..."));
                try {
                    createLandscapePdfDocument(finalImage, finalFileName, finalFunctionName);
                    Platform.runLater(() -> {
                        addMessage("✅ PDF (альбомный) успешно сохранен: " + new File(finalFileName).getName());
                        addMessage("📁 Путь: " + finalFileName);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> addMessage("❌ Ошибка создания PDF: " + e.getMessage()));
                    throw e;
                }
                return null;
            }
        };

        saveTask.setOnFailed(e -> {
            Throwable ex = saveTask.getException();
            Platform.runLater(() -> addMessage("❌ Ошибка сохранения: " + ex.getMessage()));
        });
        executor.submit(saveTask);
    }



    private void createLandscapePdfDocument(BufferedImage graphImage, String filePath, String functionName) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setNonStrokingColor(255, 255, 255);
                contentStream.addRect(0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                contentStream.fill();

                PDFont russianFont = loadRussianFont(document);

                contentStream.beginText();
                contentStream.setFont(russianFont, 14);
                contentStream.newLineAtOffset(50, 520);
                contentStream.showText("Граф функции: " + functionName.replace("_", " "));
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(russianFont, 10);
                contentStream.newLineAtOffset(50, 500);
                contentStream.showText("Создано: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
                contentStream.endText();

                float margin = 50;
                float pageWidth = PDRectangle.A4.getHeight();
                float pageHeight = PDRectangle.A4.getWidth();
                float maxImageWidth = pageWidth - (2 * margin);
                float maxImageHeight = pageHeight - 100;

                float imageAspectRatio = (float) graphImage.getWidth() / graphImage.getHeight();
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
                float imageY = pageHeight - 50 - imageHeight;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(graphImage, "PNG", baos);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, baos.toByteArray(), "graph");
                contentStream.drawImage(pdImage, imageX, imageY, imageWidth, imageHeight);
            }

            document.save(filePath);
        }
    }

    private void setupFunctionsTable() {
        TableView<FunctionInfo> table = mainController.getFunctionsTable();
        table.getColumns().clear();

        IconManager iconManager = IconManager.getInstance();

        // Колонка 1 — шарики
        TableColumn<FunctionInfo, FunctionInfo> indicatorsCol = new TableColumn<>("");
        indicatorsCol.setPrefWidth(60);
        indicatorsCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue()));
        indicatorsCol.setCellFactory(col -> new TableCell<>() {
            private final HBox hbox = new HBox(3);
            { hbox.setAlignment(Pos.CENTER_LEFT); }

            @Override
            protected void updateItem(FunctionInfo func, boolean empty) {
                super.updateItem(func, empty);
                if (empty || func == null) {
                    setGraphic(null);
                } else {
                    hbox.getChildren().clear();
                    Map<String, Color> colors = Map.of(
                            "Блокировка", Color.web("#A9A9A9"),
                            "Транзакция", Color.web("#8B4513"),
                            "ВызовФункции", Color.web("#E3F2FD"),
                            "Запрос", Color.web("#FFEAA7"),
                            "Цикл", Color.web("#45B7D1"));
                    Map<String, Integer> counts = countElementsInFunctionById(func.getFunctionId());
                    String[] order = {"Блокировка", "Транзакция", "ВызовФункции", "Запрос", "Цикл"};
                    for (String type : order) {
                        if (counts.getOrDefault(type, 0) > 0) {
                            Circle circle = new Circle(4, colors.get(type));
                            circle.setStroke(Color.BLACK);
                            Tooltip.install(circle, new Tooltip(type + ": " + counts.get(type) + " шт"));
                            hbox.getChildren().add(circle);
                            if (hbox.getChildren().size() >= 5) break;
                        }
                    }
                    setGraphic(hbox);
                }
            }
        });

        // Колонка 2 — иконка объекта (используем ConfigTreeManager синглтон)
        TableColumn<FunctionInfo, String> iconCol = new TableColumn<>("");
        iconCol.setPrefWidth(30);
        iconCol.setCellValueFactory(cellData -> {
            String objectFullName = cellData.getValue().getObjectName();
            if (objectFullName == null) return new javafx.beans.property.SimpleStringProperty("conf");
            String objectType = objectFullName.split("\\.")[0];
            String iconKey = ConfigTreeManager.getInstance().getIconKeyByRussianType(objectType);
            return new javafx.beans.property.SimpleStringProperty(iconKey);
        });
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

        // Колонка 3 — название функции
        TableColumn<FunctionInfo, String> nameCol = new TableColumn<>("Функция");
        nameCol.setPrefWidth(250);
        nameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());

        // Колонка 4 — объект метаданных
        TableColumn<FunctionInfo, String> objectCol = new TableColumn<>("Объект");
        objectCol.setPrefWidth(200);
        objectCol.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        cellData.getValue().getObjectName()));

        table.getColumns().addAll(indicatorsCol, iconCol, nameCol, objectCol);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                onFunctionSelected(newSelection);
            }
        });

        table.setItems(functionsList);

        // Настройка копирования (как на 5-й вкладке)
        setupFunctionsTableCopy();
    }





    // ========== РАБОЧАЯ СИСТЕМА КОПИРОВАНИЯ ДЛЯ ТАБЛИЦЫ (как на 5-й вкладке) ==========
    private void setupFunctionsTableCopy() {
        TableView<FunctionInfo> table = mainController.getFunctionsTable();
        if (table == null) return;

        // По умолчанию - выделение строк
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Контекстное меню
        ContextMenu contextMenu = new ContextMenu();

        MenuItem copyItem = new MenuItem("📋 Копировать выделенное");
        copyItem.setOnAction(e -> copyFunctionsSelectionToClipboard(table));

        MenuItem copyRowItem = new MenuItem("📋 Копировать строку");
        copyRowItem.setOnAction(e -> copyFunctionsCurrentRowToClipboard(table));

        MenuItem copyCellItem = new MenuItem("📋 Копировать ячейку");
        copyCellItem.setOnAction(e -> copyFunctionsCurrentCellToClipboard(table));

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem selectRowModeItem = new MenuItem("✓ Режим: выделение строк");
        selectRowModeItem.setOnAction(e -> setFunctionsRowSelectionMode(table));

        MenuItem selectCellModeItem = new MenuItem("☐ Режим: выделение ячеек");
        selectCellModeItem.setOnAction(e -> setFunctionsCellSelectionMode(table));

        contextMenu.getItems().addAll(copyItem, copyRowItem, copyCellItem, separator,
                selectRowModeItem, selectCellModeItem);
        table.setContextMenu(contextMenu);

        // Ctrl+C - копирует то, что выделено (ячейки или строки)
        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copyFunctionsSelectionToClipboard(table);
                event.consume();
            }
            // Ctrl+R - переключить режим выделения
            if (event.isControlDown() && event.getCode() == KeyCode.R) {
                if (table.getSelectionModel().isCellSelectionEnabled()) {
                    setFunctionsRowSelectionMode(table);
                } else {
                    setFunctionsCellSelectionMode(table);
                }
                event.consume();
            }
        });

        // Визуальная индикация режима
        updateFunctionsSelectionModeStatus(table);
    }

    private void setFunctionsRowSelectionMode(TableView<FunctionInfo> table) {
        table.getSelectionModel().setCellSelectionEnabled(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        addMessage("📌 Режим выделения: строки (можно выделять Ctrl+Click / Shift+Click)");
        updateFunctionsSelectionModeStatus(table);
    }

    private void setFunctionsCellSelectionMode(TableView<FunctionInfo> table) {
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        addMessage("🔲 Режим выделения: ячейки (Ctrl+C для копирования)");
        updateFunctionsSelectionModeStatus(table);
    }

    private void updateFunctionsSelectionModeStatus(TableView<FunctionInfo> table) {
        Platform.runLater(() -> {
            if (mainController.getSelectedFunctionLabel() != null) {
                String mode = table.getSelectionModel().isCellSelectionEnabled() ? "🔲 режим: ячейки" : "📌 режим: строки";
                // Используем существующий label для статуса
                mainController.getSelectedFunctionLabel().setText(mode + " | Ctrl+R - переключить");
            }
        });
    }

    private void copyFunctionsSelectionToClipboard(TableView<FunctionInfo> table) {
        if (table.getSelectionModel().isCellSelectionEnabled()) {
            copyFunctionsSelectedCellsToClipboard(table);
        } else {
            copyFunctionsSelectedRowsToClipboard(table);
        }
    }

    private void copyFunctionsSelectedRowsToClipboard(TableView<FunctionInfo> table) {
        ObservableList<FunctionInfo> selectedRows = table.getSelectionModel().getSelectedItems();

        if (selectedRows.isEmpty()) {
            addMessage("⚠️ Нет выделенных строк");
            return;
        }

        StringBuilder sb = new StringBuilder();

        // Заголовки колонок
        for (TableColumn<FunctionInfo, ?> column : table.getColumns()) {
            String header = column.getText();
            if (header != null && !header.isEmpty()) {
                sb.append(header).append("\t");
            }
        }
        sb.append("\n");

        // Данные - убираем getLineCount и getElementCount, оставляем только существующие поля
        for (FunctionInfo func : selectedRows) {
            sb.append(func.getName()).append("\t")
                    .append(func.getObjectName() != null ? func.getObjectName() : "").append("\t")
                    .append(func.getType() != null ? func.getType() : "").append("\n");
        }

        copyFunctionsToClipboard(sb.toString());
        addMessage("📋 Скопировано " + selectedRows.size() + " строк");
    }

    private void copyFunctionsCurrentRowToClipboard(TableView<FunctionInfo> table) {
        FunctionInfo selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            addMessage("⚠️ Нет выделенной строки");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(selected.getName()).append("\t")
                .append(selected.getObjectName() != null ? selected.getObjectName() : "").append("\t")
                .append(selected.getType() != null ? selected.getType() : "");

        copyFunctionsToClipboard(sb.toString());
        addMessage("📋 Скопирована строка: " + selected.getName());
    }



    private void copyFunctionsCurrentCellToClipboard(TableView<FunctionInfo> table) {
        TablePosition<?, ?> focusedCell = table.getFocusModel().getFocusedCell();
        if (focusedCell == null) {
            addMessage("⚠️ Нет выделенной ячейки");
            return;
        }

        Object value = table.getColumns().get(focusedCell.getColumn()).getCellData(focusedCell.getRow());
        if (value != null) {
            String text = value.toString();
            String preview = text.length() > 50 ? text.substring(0, 50) + "..." : text;
            copyFunctionsToClipboard(text);
            addMessage("📋 Скопирована ячейка: " + preview);
        } else {
            addMessage("⚠️ Ячейка пуста");
        }
    }

    private void copyFunctionsSelectedCellsToClipboard(TableView<FunctionInfo> table) {
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

        copyFunctionsToClipboard(sb.toString());
        addMessage("📋 Скопировано " + selectedCells.size() + " ячеек");
    }

    private void copyFunctionsToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }






    private void onTreeNodeSelected(TreeItem<String> selectedNode) {
        String nodeName = selectedNode.getValue();
        addMessage("🔍 Выбран узел: " + nodeName);

        functionsList.clear();
        mainController.getFunctionsCountLabel().setText("Функций: 0");
        mainController.getSelectedFunctionLabel().setText("Выберите функцию");
        graphHistory.clear();
        updateBackButton();
        elementGraphView.buildCompleteGraph("Выберите функцию", new ArrayList<>(), 0);

        ObjectInfo objectInfo = determineObjectInfo(selectedNode);
        if (objectInfo != null) {
            addMessage("📁 Загрузка функций для: " + objectInfo.getFullName() + " (" + objectInfo.getModuleType() + ")");
            loadFunctionsForObject(objectInfo);
        } else {
            if (isModuleNode(selectedNode)) {
                addMessage("⚠️ Не удалось определить объект для модуля: " + nodeName);
            } else {
                addMessage("ℹ️ Выберите конкретный модуль для просмотра функций");
            }
        }
    }

    private void onFunctionSelected(FunctionInfo function) {
        mainController.getSelectedFunctionLabel().setText(function.getName() + " (" + function.getType() + ")");
        addMessage("🔍 Анализ функции: " + function.getName());

        graphHistory.clear();
        updateBackButton();
        buildElementGraphForFunction(function);
        loadModuleCodeForFunction(function);
        OsnFunc = function;
    }

    private void onFunctionSelectedInGraph(int functionId, String functionName) {
        addMessage("🔍 Выбрана функция из графа: " + functionName);

        Task<FunctionInfo> findTask = new Task<>() {
            @Override
            protected FunctionInfo call() throws Exception {
                return findFunctionById(functionId);
            }
        };

        findTask.setOnSucceeded(e -> {
            FunctionInfo function = findTask.getValue();
            if (function != null) {
                graphHistory.add(function);
                updateBackButton();
                mainController.getSelectedFunctionLabel().setText(function.getName() + " (" + function.getType() + ")");
                buildElementGraphForFunction(function);
                loadModuleCodeForFunction(function);
            } else {
                addMessage("⚠️ Функция не найдена: " + functionName);
            }
        });
        executor.submit(findTask);
    }

    public void buildElementGraphForFunction(FunctionInfo function) {
        Task<List<CodeElement>> task = new Task<>() {
            @Override
            protected List<CodeElement> call() throws Exception {

                ElementGraphView.clearCallGraphCache(); // очищаем ДО preload

                // ШАГ 1: грузим элементы из БД
                List<CodeElement> elements = loadCodeElementsForFunction(function);

                // ШАГ 2: прогреваем кэш графа — все SQL в фоне
                elementGraphView.preloadGraphData(function.getFunctionId(), elements);

                return elements;
            }

            @Override
            protected void succeeded() {
                List<CodeElement> elements = getValue();
                // ШАГ 3: только отрисовка на UI-потоке — БД уже не трогаем
                elementGraphView.buildCompleteGraph(
                        function.getName(), elements, function.getFunctionId()
                );
/*                addMessage("✅ Граф построен для " + function.getName()
                        + " (" + elements.size() + " элементов)");*/
            }

            @Override
            protected void failed() {
                addMessage("❌ Ошибка построения графа: " + getException().getMessage());
            }
        };

        executor.submit(task);
    }

    private void loadModuleCodeForFunction(FunctionInfo function) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return getFunctionTextFromDatabase(function);
            }
        };

        task.setOnSucceeded(e -> {
            String functionText = task.getValue();
            if (functionText != null && !functionText.isEmpty()) {
                mainController.getModuleCodeArea().replaceText(functionText);
                OneCHighlighter.apply1CColors(mainController.getModuleCodeArea());
             //   addMessage("✅ Загружен код функции: " + function.getName());
            } else {
                mainController.getModuleCodeArea().replaceText("// Текст функции не найден в базе данных\n");
                OneCHighlighter.apply1CColors(mainController.getModuleCodeArea());
              //  addMessage("⚠️ Текст функции не найден для: " + function.getName());
            }
        });
        executor.submit(task);
    }

    private void addMessage(String message) {
        Platform.runLater(() -> {
            mainController.getMessagesArea().appendText(message + "\n");
            mainController.getMessagesArea().setScrollTop(Double.MAX_VALUE);
        });
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

    private void updateBackButton() {
        boolean canGoBack = !graphHistory.history.isEmpty();
        mainController.getBackButton().setVisible(canGoBack);
    }

    private void setExpanded(TreeItem<String> item, boolean expanded) {
        item.setExpanded(expanded);
        for (TreeItem<String> child : item.getChildren()) {
            setExpanded(child, expanded);
        }
    }

    private TreeItem<String> filterTree(TreeItem<String> item, String query) {
        if (item == null) return null;

        String itemText = item.getValue().toLowerCase();
        boolean matches = itemText.contains(query);

        if (matches) {
            TreeItem<String> parentObject = findParentObject(item);
            if (parentObject != null) {
                return copySubtree(parentObject);
            }
            return copySubtree(item);
        }

        TreeItem<String> newItem = new TreeItem<>(item.getValue());
        if (item.getGraphic() != null) {
            newItem.setGraphic(item.getGraphic());
        }

        boolean hasMatchingChildren = false;
        for (TreeItem<String> child : item.getChildren()) {
            TreeItem<String> filteredChild = filterTree(child, query);
            if (filteredChild != null) {
                newItem.getChildren().add(filteredChild);
                hasMatchingChildren = true;
            }
        }

        return matches || hasMatchingChildren ? newItem : null;
    }

    private TreeItem<String> findParentObject(TreeItem<String> node) {
        TreeItem<String> current = node.getParent();

        while (current != null) {
            String name = current.getValue();
            if (isObjectNode(current)) {
                return current;
            }
            current = current.getParent();
        }

        return null;
    }

    private boolean isObjectNode(TreeItem<String> node) {
        if (node == null) return false;

        TreeItem<String> parent = node.getParent();
        if (parent == null) return false;

        String parentName = parent.getValue();
        String[] objectGroups = {
                "Документы", "Справочники", "Регистры сведений", "Регистры накопления",
                "Регистры бухгалтерии", "Обработки", "Отчёты", "Планы обмена",
                "Журналы документов", "Планы счетов", "Планы видов характеристик",
                "Веб-сервисы", "HTTP-сервисы", "Критерии отбора", "Хранилища настроек"
        };

        for (String group : objectGroups) {
            if (group.equals(parentName)) {
                return true;
            }
        }

        return false;
    }

    private TreeItem<String> copySubtree(TreeItem<String> original) {
        TreeItem<String> copy = new TreeItem<>(original.getValue());
        if (original.getGraphic() != null) {
            copy.setGraphic(original.getGraphic());
        }

        for (TreeItem<String> child : original.getChildren()) {
            copy.getChildren().add(copySubtree(child));
        }

        return copy;
    }

    private void expandAll(TreeItem<String> item) {
        if (item == null) return;
        item.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) {
            expandAll(child);
        }
    }

    private void loadTreeFromDb() {
        try {
            ConfigTreeManager manager = new ConfigTreeManager();
            TreeItem<String> root = manager.buildConfigTreeFromDb();
            mainController.getClassTree().setRoot(root);
            mainController.getClassTree().setShowRoot(true);
            originalTreeRoot = null;
        } catch (Exception e) {
            addMessage("❌ Ошибка загрузки из БД: " + e.getMessage());
        }
    }



    private void loadFunctionsForObject(ObjectInfo objectInfo) {
        Task<List<FunctionInfo>> task = new Task<>() {
            @Override
            protected List<FunctionInfo> call() throws Exception {
                return loadFunctionsFromDatabase(objectInfo);
            }
        };

        task.setOnSucceeded(e -> {
            List<FunctionInfo> functions = task.getValue();
            functionsList.setAll(functions);
            mainController.getFunctionsCountLabel().setText("Функций: " + functions.size());
            addMessage("✅ Загружено функций: " + functions.size() + " для " + objectInfo.getFullName());
        });
        executor.submit(task);
    }

    private List<FunctionInfo> loadFunctionsFromDatabase(ObjectInfo objectInfo) {
        List<FunctionInfo> functions = new ArrayList<>();
        String sql;
        boolean useModuleName = false;

        if ("Form".equals(objectInfo.getModuleType()) || "Command".equals(objectInfo.getModuleType())) {
            sql = "SELECT mf.function_name, mf.function_type, (mf.end_line - mf.start_line) as line_count, COUNT(ce.id) as element_count, mf.module_id, mm.object_full_name, mf.id as function_id FROM metadata_functions mf JOIN metadata_modules mm ON mf.module_id = mm.id LEFT JOIN code_elements ce ON mf.module_id = ce.module_id AND mf.function_name = ce.function_name WHERE mm.object_full_name = ? AND mm.module_type = ? AND mm.module_name = ? GROUP BY mf.function_name, mf.function_type, mf.id, mf.module_id ORDER BY mf.function_name";
            useModuleName = true;
        } else {
            sql = "SELECT mf.function_name, mf.function_type, (mf.end_line - mf.start_line) as line_count, COUNT(ce.id) as element_count, mf.module_id, mm.object_full_name, mf.id as function_id FROM metadata_functions mf JOIN metadata_modules mm ON mf.module_id = mm.id LEFT JOIN code_elements ce ON mf.module_id = ce.module_id AND mf.function_name = ce.function_name WHERE mm.object_full_name = ? AND mm.module_type = ? AND (mm.module_name IS NULL OR mm.module_name = '') GROUP BY mf.function_name, mf.function_type, mf.id, mf.module_id ORDER BY mf.function_name";
        }

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, objectInfo.getFullName());
            ps.setString(2, objectInfo.getModuleType());

            if (useModuleName) {
                ps.setString(3, objectInfo.getModuleName());
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                FunctionInfo func = new FunctionInfo(
                        rs.getString("function_name"),
                        rs.getString("function_type"),
                        rs.getInt("line_count"),
                        rs.getInt("element_count"),
                        rs.getInt("module_id"),
                        rs.getString("object_full_name"),
                        rs.getInt("function_id")
                );
                functions.add(func);
            }
        } catch (SQLException e) {}
        return functions;
    }

    private FunctionInfo findFunctionById(int functionId) {
        String sql = "SELECT mf.function_name, mf.function_type, (mf.end_line - mf.start_line) as line_count, COUNT(ce.id) as element_count, mf.module_id, mm.object_full_name, mf.id as function_id FROM metadata_functions mf JOIN metadata_modules mm ON mf.module_id = mm.id LEFT JOIN code_elements ce ON mf.module_id = ce.module_id AND mf.function_name = ce.function_name WHERE mf.id = ? GROUP BY mf.function_name, mf.function_type, mf.id, mf.module_id";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, functionId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return new FunctionInfo(
                        rs.getString("function_name"),
                        rs.getString("function_type"),
                        rs.getInt("line_count"),
                        rs.getInt("element_count"),
                        rs.getInt("module_id"),
                        rs.getString("object_full_name"),
                        rs.getInt("function_id")
                );
            }
        } catch (SQLException e) {}
        return null;
    }

    private List<CodeElement> loadCodeElementsForFunction(FunctionInfo function) {
        List<CodeElement> elements = new ArrayList<>();
        String sql = "SELECT ce.id, ce.element_type, ce.element_name, ce.owner_name, ce.start_line, ce.end_line, ce.element_text, ce.function_id, ce.called_function_id, (SELECT ce2.id FROM code_elements ce2 WHERE ce2.module_id = ce.module_id AND ce2.function_name = ce.function_name AND ce2.element_name = ce.owner_name LIMIT 1) as owner_element_id FROM code_elements ce WHERE ce.module_id = ? AND ce.function_name = ? ORDER BY ce.start_line";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, function.getModuleId());
            ps.setString(2, function.getName());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                CodeElement element = new CodeElement();
                element.id = rs.getInt("id");
                element.type = rs.getString("element_type");
                element.subtype = rs.getString("element_name");
                element.startLine = rs.getInt("start_line");
                element.endLine = rs.getInt("end_line");
                element.text = rs.getString("element_text");
                element.ownerName = rs.getString("owner_name");
                element.function_id = rs.getInt("function_id");
                element.calledFunctionId = rs.getInt("called_function_id");

                int ownerIdFromDb = rs.getInt("owner_element_id");
                if (!rs.wasNull()) {
                    element.ownerElementId = String.valueOf(ownerIdFromDb);
                } else {
                    element.ownerElementId = null;
                }
                elements.add(element);
            }
        } catch (SQLException e) {}
        return elements;
    }

    private String getFunctionTextFromDatabase(FunctionInfo function) {
        String sql = "SELECT function_text FROM metadata_functions WHERE id = ? LIMIT 1";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, function.getFunctionId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("function_text");
            }
        } catch (SQLException e) {}
        return null;
    }

    private Map<String, Integer> countElementsInFunctionById(int functionId) {
        Map<String, Integer> dbCounts = new HashMap<>();
        String sql = "SELECT element_type, COUNT(*) as cnt FROM code_elements WHERE function_id = ? GROUP BY element_type";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, functionId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String dbType = rs.getString("element_type");
                int count = rs.getInt("cnt");
                dbCounts.put(dbType, count);
            }
        } catch (SQLException e) {}
        return ElementColors.groupElementCounts(dbCounts);
    }

    private ObjectInfo determineObjectInfo(TreeItem<String> selectedNode) {
        if (selectedNode == null || selectedNode.getParent() == null) {
            return null;
        }

        String nodeName = selectedNode.getValue();
        String parentName = selectedNode.getParent().getValue();

        if ("Общие модули".equals(parentName)) {
            return new ObjectInfo("ОбщийМодуль." + nodeName, "ОбщийМодуль", -1, "Module", null);
        }
        if ("Общие формы".equals(parentName)) {
            return new ObjectInfo("ОбщаяФорма." + nodeName, "ОбщаяФорма", -1, "Module", null);
        }
        if ("Общие команды".equals(parentName)) {
            return new ObjectInfo("ОбщаяКоманда." + nodeName, "ОбщаяКоманда", -1, "Module", null);
        }

        if ("Веб-сервисы".equals(parentName)) {
            return new ObjectInfo("ВебСервис." + nodeName, "ВебСервис", -1, "Module", null);
        }

        if ("HTTP-сервисы".equals(parentName)) {
            return new ObjectInfo("HTTPService." + nodeName, "HTTPService", -1, "Module", null);
        }

        if ("Формы".equals(parentName) || "Команды".equals(parentName)) {
            return getObjectInfoFromFormOrCommandNode(selectedNode);
        }

        if (isModuleNode(selectedNode)) {
            return getObjectInfoFromModuleNode(selectedNode);
        }

        Set<String> groupsWithBranches = Set.of(
                "Документы", "Справочники", "Регистры сведений", "Регистры накопления",
                "Регистры бухгалтерии", "Регистры расчетов",
                "Обработки", "Отчёты", "Планы обмена",
                "Журналы документов", "Планы счетов", "Планы видов характеристик",
                "Планы видов расчета",
                "Веб-сервисы", "HTTP-сервисы", "Критерии отбора", "Хранилища настроек",
                "Бизнес-процессы", "Задачи"   // ← добавить
        );


        if (groupsWithBranches.contains(parentName)) {
            boolean hasBranches = false;
            for (TreeItem<String> child : selectedNode.getChildren()) {
                String childName = child.getValue();
                if (childName.equals("МодульОбъекта") || childName.equals("МодульМенеджера") || childName.equals("МодульНабораЗаписей") || childName.equals("Формы") || childName.equals("Команды")) {
                    hasBranches = true;
                    break;
                }
            }

            if (hasBranches) {
                return null;
            }
        }

        if ("Журналы документов".equals(parentName)) {
            return new ObjectInfo("ЖурналДокументов." + nodeName, "DocumentJournal", -1, "Manager", null);
        }

        if ("Планы счетов".equals(parentName)) {
            return new ObjectInfo("ChartsOfAccounts." + nodeName, "ChartOfAccounts", -1, "Object", null);
        }

        if ("Планы видов характеристик".equals(parentName)) {
            return new ObjectInfo("ChartsOfCharacteristicTypes." + nodeName, "ChartOfCharacteristicTypes", -1, "Object", null);
        }

        if ("Перечисления".equals(parentName)) {
            return new ObjectInfo("Перечисление." + nodeName, "Enum", -1, "Manager", null);
        }

        if ("Константы".equals(parentName)) {
            return new ObjectInfo("Константа." + nodeName, "Constant", -1, "Manager", null);
        }

        String objectType = getObjectTypeFromParent(parentName);
        if (objectType != null) {
            String fullName = objectType + "." + nodeName;
            String moduleType = determineModuleTypeForObjectType(objectType);
            return new ObjectInfo(fullName, objectType, -1, moduleType, null);
        }

        return null;
    }

    private String determineModuleTypeForObjectType(String objectType) {
        switch (objectType) {
            case "Enum":
            case "Constant":
                return "Manager";

            case "WebService":
            case "HTTPService":
            case "CommonModule":
            case "CommonForm":
            case "CommonCommand":
            case "ExchangePlan":
            case "FilterCriteria":
            case "SettingsStorage":
                return "Module";

            default:
                return "Object"; // Document, Catalog, Register, BusinessProcess, Task и т.д.
        }
    }
    private ObjectInfo getObjectInfoFromFormOrCommandNode(TreeItem<String> node) {
        if (node == null || node.getParent() == null) {
            return null;
        }

        TreeItem<String> parent = node.getParent();
        String parentName = parent.getValue();

        if ("Формы".equals(parentName)) {
            return getObjectInfoFromFormNode(node);
        } else if ("Команды".equals(parentName)) {
            return getObjectInfoFromCommandNode(node);
        }
        return null;
    }

    private ObjectInfo getObjectInfoFromCommandNode(TreeItem<String> commandNode) {
        if (commandNode == null || commandNode.getParent() == null) return null;

        String commandName = commandNode.getValue();
        TreeItem<String> objectNode = commandNode.getParent().getParent();
        if (objectNode == null) return null;

        String objectName = objectNode.getValue();
        String parentCategory = objectNode.getParent() != null ? objectNode.getParent().getValue() : "";

        String objectType = getObjectTypeFromParent(parentCategory);
        if (objectType != null) {
            String fullName;
            if ("DocumentJournal".equals(objectType)) {
                fullName = "ЖурналДокументов." + objectName;
            } else if ("ChartOfAccounts".equals(objectType)) {
                fullName = "ChartsOfAccounts." + objectName;
            } else if ("ChartOfCharacteristicTypes".equals(objectType)) {
                fullName = "ChartsOfCharacteristicTypes." + objectName;
            } else if ("Enum".equals(objectType)) {
                fullName = "Перечисление." + objectName;
            } else if ("Constant".equals(objectType)) {
                fullName = "Константа." + objectName;
            } else {
                fullName = objectType + "." + objectName;
            }
            return new ObjectInfo(fullName, objectType, -1, "Command", commandName);
        }
        return null;
    }

    private ObjectInfo getObjectInfoFromFormNode(TreeItem<String> formNode) {
        if (formNode == null || formNode.getParent() == null) return null;

        String formName = formNode.getValue();
        TreeItem<String> objectNode = formNode.getParent().getParent();
        if (objectNode == null) return null;

        String objectName = objectNode.getValue();
        String parentCategory = objectNode.getParent() != null ? objectNode.getParent().getValue() : "";

        String objectType = getObjectTypeFromParent(parentCategory);
        if (objectType != null) {
            String fullName;
            if ("DocumentJournal".equals(objectType)) {
                fullName = "ЖурналДокументов." + objectName;
            } else if ("ChartOfAccounts".equals(objectType)) {
                fullName = "ChartsOfAccounts." + objectName;
            } else if ("ChartOfCharacteristicTypes".equals(objectType)) {
                fullName = "ChartsOfCharacteristicTypes." + objectName;
            } else if ("Enum".equals(objectType)) {
                fullName = "Перечисление." + objectName;
            } else if ("Constant".equals(objectType)) {
                fullName = "Константа." + objectName;
            } else {
                fullName = objectType + "." + objectName;
            }
            return new ObjectInfo(fullName, objectType, -1, "Form", formName);
        }
        return null;
    }

    private ObjectInfo getObjectInfoFromModuleNode(TreeItem<String> moduleNode) {
        if (moduleNode == null || moduleNode.getParent() == null) return null;

        String moduleName = moduleNode.getValue();
        TreeItem<String> objectNode = moduleNode.getParent();
        String objectName = objectNode.getValue();
        String parentCategory = objectNode.getParent() != null ? objectNode.getParent().getValue() : "";

        String objectType = getObjectTypeFromParent(parentCategory);
        if (objectType != null) {
            String fullName;
            if ("DocumentJournal".equals(objectType)) {
                fullName = "ЖурналДокументов." + objectName;
            } else if ("ChartOfAccounts".equals(objectType)) {
                fullName = "ChartsOfAccounts." + objectName;
            } else if ("ChartOfCharacteristicTypes".equals(objectType)) {
                fullName = "ChartsOfCharacteristicTypes." + objectName;
            } else if ("Enum".equals(objectType)) {
                fullName = "Перечисление." + objectName;
            } else if ("Constant".equals(objectType)) {
                fullName = "Константа." + objectName;
            } else {
                fullName = objectType + "." + objectName;
            }

            String moduleType = getModuleTypeFromNodeName(moduleName);
            return new ObjectInfo(fullName, objectType, -1, moduleType, moduleName);
        }
        return null;
    }

    private String getModuleTypeFromNodeName(String moduleName) {
        String cleanName = moduleName.replace(" ", "").toLowerCase();

        if (cleanName.equals("модульобъекта")) {
            return "Object";
        } else if (cleanName.equals("модульменеджера")) {
            return "Manager";
        } else if (cleanName.equals("модульменеджеразначения")) {
            return "ValueManager";
        } else if (cleanName.equals("модульнаборазаписей")) {
            return "RecordSet";
        } else if (cleanName.equals("формадокумента") || cleanName.equals("формасписка") || cleanName.equals("формавыбора") || cleanName.equals("формагруппы")) {
            return "Form";
        } else if (cleanName.equals("команда")) {
            return "Command";
        }
        return "Unknown";
    }

    private boolean isModuleNode(TreeItem<String> node) {
        if (node == null || node.getParent() == null) return false;

        String nodeName = node.getValue();
        String parentName = node.getParent().getValue();
        String grandParentName = null;
        if (node.getParent().getParent() != null) {
            grandParentName = node.getParent().getParent().getValue();
        }

        if ("Формы".equals(parentName) || "Команды".equals(parentName)) {
            return true;
        }

        String[] objectTypes = {
                "Документы", "Справочники", "Регистры сведений", "Регистры накопления",
                "Регистры бухгалтерии", "Регистры расчетов",        // ← добавить
                "Обработки", "Отчёты", "Перечисления", "Константы",
                "Планы обмена", "Журналы документов", "Планы счетов",
                "Планы видов характеристик", "Планы видов расчета",  // ← добавить
                "Веб-сервисы", "HTTP-сервисы", "Критерии отбора",
                "Хранилища настроек", "Бизнес-процессы", "Задачи"   // ← добавить
        };


        for (String objectType : objectTypes) {
            if (objectType.equals(grandParentName)) {
                return nodeName.equals("МодульОбъекта") || nodeName.equals("МодульМенеджера") || nodeName.equals("МодульНабораЗаписей");
            }
        }
        return false;
    }

    private String getObjectTypeFromParent(String parentName) {
        if (parentName == null) return null;

        switch (parentName) {
            case "Документы":                  return "Документ";
            case "Справочники":                return "Справочник";
            case "Регистры сведений":          return "РегистрСведений";
            case "Регистры накопления":        return "РегистрНакопления";
            case "Регистры бухгалтерии":       return "РегистрБухгалтерии";
            case "Регистры расчетов":          return "РегистрРасчетов";
            case "Обработки":                  return "Обработка";
            case "Отчёты":                     return "Отчет";
            case "Перечисления":               return "Перечисление";
            case "Константы":                  return "Константа";
            case "Планы обмена":               return "ПланОбмена";
            case "Журналы документов":         return "ЖурналДокументов";
            case "Критерии отбора":            return "КритерийОтбора";      // было "FilterCriteria"
            case "Хранилища настроек":         return "ХранилищеНастроек";
            case "Веб-сервисы":                return "ВебСервис";
            case "HTTP-сервисы":               return "HTTPService";
            case "Планы счетов":               return "ChartsOfAccounts";    // было "ChartOfAccounts"
            case "Планы видов характеристик":  return "ChartsOfCharacteristicTypes"; // было "ChartOfCharacteristicTypes"
            case "Планы видов расчета":        return "ПланВидовРасчета";
            case "Общие модули":               return "ОбщийМодуль";
            case "Общие формы":                return "ОбщаяФорма";
            case "Общие команды":              return "ОбщаяКоманда";
            case "Бизнес-процессы":            return "БизнесПроцесс";       // было "BusinessProcess"
            case "Задачи":                     return "Задача";              // было "Task"
            default: return null;
        }
    }

    public void shutdown() {
        saveNastroek();
        if (settings != null) settings.close();
    }
}