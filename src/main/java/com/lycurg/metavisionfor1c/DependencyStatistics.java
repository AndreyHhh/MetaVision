package com.lycurg.metavisionfor1c;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import java.sql.*;
import java.util.*;


public class DependencyStatistics {

    private final DependencyController mainController;
    private final ConfigTreeManager configTreeManager;

    public DependencyStatistics(DependencyController controller) {
        this.mainController = controller;
        this.configTreeManager = ConfigTreeManager.getInstance();
    }

    public void initialize() {
    }

    public GridPane createStatisticsTab() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(20);
        grid.setVgap(20);
        grid.setStyle("-fx-background-color: #f8f9fa;");

        Map<String, Object> stats = loadStatisticsFromDb();
        Map<String, Integer> elementTypeCounts = getElementTypeCountsFromDb();

        int row = 0;
        int col = 0;

        VBox topRowContainer = new VBox(15);
        topRowContainer.setAlignment(Pos.CENTER);
        topRowContainer.setPadding(new Insets(10));
        topRowContainer.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                "-fx-border-color: #d1d8e0; -fx-border-radius: 10px; " +
                "-fx-border-width: 1px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);");

        Label topRowTitle = new Label("ОБЩАЯ СТАТИСТИКА");
        topRowTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1b5e20;");

        HBox topCardsRow = new HBox(20);
        topCardsRow.setAlignment(Pos.CENTER);

        topCardsRow.getChildren().addAll(
                createLargeStatsCard("\uD83E\uDE99", "Объектов", stats.get("total_objects")),
                createLargeStatsCard("\uD83D\uDCDA", "Модулей", stats.get("total_modules")),
                createLargeStatsCard("✎", "Функций", stats.get("total_functions")),
                createLargeStatsCard("⛓", "Элементов", stats.get("total_elements"))
        );

        topRowContainer.getChildren().addAll(topRowTitle, topCardsRow);
        grid.add(topRowContainer, col, row++, 2, 1);

        VBox bottomRowContainer = new VBox(15);
        bottomRowContainer.setAlignment(Pos.CENTER);
        bottomRowContainer.setPadding(new Insets(10));
        bottomRowContainer.setStyle("-fx-background-color: white; -fx-background-radius: 10px; " +
                "-fx-border-color: #d1d8e0; -fx-border-radius: 10px; " +
                "-fx-border-width: 1px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);");

        Label bottomRowTitle = new Label("ТИПЫ ЭЛЕМЕНТОВ КОДА");
        bottomRowTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1b5e20;");

        HBox bottomCardsRow = new HBox(15);
        bottomCardsRow.setAlignment(Pos.CENTER);

        String[] elementKeys = {"Запрос", "ЦиклНезависимый", "Транзакция", "Блокировка"};
        String[] elementIcons = {"📋", "↻", "\uD83D\uDCE6", "🔒"};
        String[] elementNames = {"Запросы", "Циклы", "Транзакции", "Блокировки"};

        for (int i = 0; i < elementKeys.length; i++) {
            int count = getCountForElementType(elementTypeCounts, elementKeys[i]);
            bottomCardsRow.getChildren().add(
                    createSmallStatsCard(elementIcons[i], elementNames[i], count)
            );
        }

        bottomRowContainer.getChildren().addAll(bottomRowTitle, bottomCardsRow);
        grid.add(bottomRowContainer, col, row++, 2, 1);

        ScrollPane metadataTable = createMetadataTable(stats);
        grid.add(metadataTable, 0, row);
        GridPane.setVgrow(metadataTable, Priority.ALWAYS);

        VBox chartsColumn = new VBox(20);

        VBox chartBox1 = new VBox(15);
        chartBox1.setStyle("-fx-background-color: white; -fx-background-radius: 10px; -fx-padding: 20px; " +
                "-fx-border-color: #d1d8e0; -fx-border-radius: 10px; -fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);");

        Label chartTitle1 = new Label("РАСПРЕДЕЛЕНИЕ ОБЪЕКТОВ");
        chartTitle1.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1b5e20;");

        PieChart objectChart = createObjectTypeChart(stats);
        objectChart.setPrefSize(380, 280);

        chartBox1.getChildren().addAll(chartTitle1, objectChart);

        VBox chartBox2 = new VBox(15);
        chartBox2.setStyle("-fx-background-color: white; -fx-background-radius: 10px; -fx-padding: 20px; " +
                "-fx-border-color: #d1d8e0; -fx-border-radius: 10px; -fx-border-width: 1px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 3);");

        Label chartTitle2 = new Label("ТИПЫ ЭЛЕМЕНТОВ КОДА");
        chartTitle2.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1b5e20;");

        BarChart<String, Number> elementChart = createElementTypeChart(stats);
        elementChart.setPrefSize(380, 280);

        chartBox2.getChildren().addAll(chartTitle2, elementChart);

        chartsColumn.getChildren().addAll(chartBox1, chartBox2);
        grid.add(chartsColumn, 1, row);

        ColumnConstraints leftCol = new ColumnConstraints();
        leftCol.setPercentWidth(60);

        ColumnConstraints rightCol = new ColumnConstraints();
        rightCol.setPercentWidth(40);

        grid.getColumnConstraints().addAll(leftCol, rightCol);

        return grid;
    }

    private ScrollPane createMetadataTable(Map<String, Object> stats) {
        VBox container = new VBox(10);
        container.setPadding(new Insets(15));
        container.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; " +
                "-fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label title = new Label("Метаданные конфигурации");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1b5e20;");

        TableView<MetadataItem> tableView = new TableView<>();
        tableView.setPrefHeight(400);

        // КОЛОНКА С ИКОНКАМИ - используем английский тип для получения иконки
        TableColumn<MetadataItem, String> iconColumn = new TableColumn<>("");
        iconColumn.setPrefWidth(40);
        iconColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(
                        configTreeManager.getIconKeyByDbObjectType(cellData.getValue().getEnglishType())
                ));
        iconColumn.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            @Override
            protected void updateItem(String iconKey, boolean empty) {
                super.updateItem(iconKey, empty);
                if (empty || iconKey == null) {
                    setGraphic(null);
                } else {
                    Image icon = IconManager.getInstance().getIcon(iconKey);
                    if (icon != null) {
                        imageView.setImage(icon);
                        imageView.setFitWidth(16);
                        imageView.setFitHeight(16);
                        setGraphic(imageView);
                    }
                }
            }
        });

        TableColumn<MetadataItem, String> typeColumn = new TableColumn<>("Тип объекта");
        typeColumn.setPrefWidth(250);
        typeColumn.setCellValueFactory(cellData -> cellData.getValue().typeProperty());

        TableColumn<MetadataItem, Integer> countColumn = new TableColumn<>("Количество");
        countColumn.setPrefWidth(120);
        countColumn.setCellValueFactory(cellData -> cellData.getValue().countProperty().asObject());

        TableColumn<MetadataItem, String> percentColumn = new TableColumn<>("Процент");
        percentColumn.setPrefWidth(100);
        percentColumn.setCellValueFactory(cellData -> cellData.getValue().percentProperty());

        tableView.getColumns().addAll(iconColumn, typeColumn, countColumn, percentColumn);

        ObservableList<MetadataItem> data = FXCollections.observableArrayList();
        Map<String, Integer> metadataStats = (Map<String, Integer>) stats.get("metadata_stats");
        int totalObjects = (int) stats.get("total_objects");

        if (metadataStats != null) {
            for (Map.Entry<String, Integer> entry : metadataStats.entrySet()) {
                if (entry.getValue() > 0) {
                    String englishType = entry.getKey();
                    String rusType = translateObjectType(englishType);
                    double percent = totalObjects > 0 ? (entry.getValue() * 100.0 / totalObjects) : 0;

                    data.add(new MetadataItem(
                            entry.getKey(),  // английский тип (Documents, Catalogs и т.д.)
                            rusType,         // русское название
                            entry.getValue(),
                            String.format("%.1f%%", percent)
                    ));
                }
            }
        }

        data.sort((a, b) -> b.getCount() - a.getCount());
        tableView.setItems(data);

        HBox footer = new HBox(10);
        footer.setPadding(new Insets(10, 0, 0, 0));
        footer.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 10px; -fx-background-radius: 4px;");

        Label totalLabel = new Label("Всего типов: " + data.size());
        totalLabel.setStyle("-fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label sumLabel = new Label("Объектов: " + totalObjects);
        sumLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1b5e20;");

        footer.getChildren().addAll(totalLabel, spacer, sumLabel);
        container.getChildren().addAll(title, tableView, footer);

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");

        return scrollPane;
    }

    private VBox createLargeStatsCard(String icon, String title, Object value) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20, 25, 20, 25));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: linear-gradient(to bottom right, #f8f9fa, #e9ecef); " +
                "-fx-border-color: #c3e6cb; -fx-border-radius: 12px; -fx-background-radius: 12px; " +
                "-fx-border-width: 2px; -fx-effect: dropshadow(gaussian, rgba(76, 175, 80, 0.2), 8, 0, 0, 2);");
        card.setMinWidth(180);
        card.setMinHeight(120);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 32px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #546e7a; -fx-font-weight: bold;");

        Label valueLabel = new Label(formatLargeNumber(value));
        valueLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #1b5e20;");

        card.getChildren().addAll(iconLabel, titleLabel, valueLabel);

        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: linear-gradient(to bottom right, #e8f5e9, #c8e6c9); " +
                    "-fx-border-color: #4caf50; -fx-border-radius: 12px; -fx-background-radius: 12px; " +
                    "-fx-border-width: 2px; -fx-effect: dropshadow(gaussian, rgba(76, 175, 80, 0.3), 10, 0, 0, 3);");
        });

        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: linear-gradient(to bottom right, #f8f9fa, #e9ecef); " +
                    "-fx-border-color: #c3e6cb; -fx-border-radius: 12px; -fx-background-radius: 12px; " +
                    "-fx-border-width: 2px; -fx-effect: dropshadow(gaussian, rgba(76, 175, 80, 0.2), 8, 0, 0, 2);");
        });

        return card;
    }

    private VBox createSmallStatsCard(String icon, String title, int value) {
        VBox card = new VBox(5);
        card.setPadding(new Insets(15, 20, 15, 20));
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: linear-gradient(to bottom right, #e3f2fd, #bbdefb); " +
                "-fx-border-color: #64b5f6; -fx-border-radius: 8px; -fx-background-radius: 8px; " +
                "-fx-border-width: 1px; -fx-effect: dropshadow(gaussian, rgba(33, 150, 243, 0.2), 6, 0, 0, 2);");
        card.setMinWidth(140);
        card.setMinHeight(100);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 24px;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #1565c0; -fx-font-weight: bold;");
        titleLabel.setWrapText(true);
        titleLabel.setTextAlignment(TextAlignment.CENTER);

        Label valueLabel = new Label(String.valueOf(value));
        valueLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0d47a1;");

        card.getChildren().addAll(iconLabel, titleLabel, valueLabel);

        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: linear-gradient(to bottom right, #bbdefb, #90caf9); " +
                    "-fx-border-color: #2196f3; -fx-border-radius: 8px; -fx-background-radius: 8px; " +
                    "-fx-border-width: 1.5px; -fx-effect: dropshadow(gaussian, rgba(33, 150, 243, 0.3), 8, 0, 0, 3);");
        });

        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: linear-gradient(to bottom right, #e3f2fd, #bbdefb); " +
                    "-fx-border-color: #64b5f6; -fx-border-radius: 8px; -fx-background-radius: 8px; " +
                    "-fx-border-width: 1px; -fx-effect: dropshadow(gaussian, rgba(33, 150, 243, 0.2), 6, 0, 0, 2);");
        });

        return card;
    }

    private int getCountForElementType(Map<String, Integer> elementTypeCounts, String typeKey) {
        int count = 0;
        for (Map.Entry<String, Integer> entry : elementTypeCounts.entrySet()) {
            String dbType = entry.getKey();
            if (typeKey.equals("ЦиклНезависимый")) {
                if (dbType.equals("ЦиклНезависимый") || dbType.equals("ЦиклЗапроса")) {
                    count += entry.getValue();
                }
            } else if (dbType.equals(typeKey)) {
                count += entry.getValue();
            }
        }
        return count;
    }

    private String formatLargeNumber(Object value) {
        if (value instanceof Integer) {
            int num = (Integer) value;
            if (num >= 1000000) {
                return String.format("%.1fM", num / 1000000.0);
            } else if (num >= 1000) {
                return String.format("%.1fK", num / 1000.0);
            }
        }
        return String.valueOf(value);
    }

    private PieChart createObjectTypeChart(Map<String, Object> stats) {
        ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
        Map<String, Integer> objectTypes = (Map<String, Integer>) stats.get("object_types");
        if (objectTypes != null) {
            for (Map.Entry<String, Integer> entry : objectTypes.entrySet()) {
                if (entry.getValue() > 0) {
                    pieChartData.add(new PieChart.Data(
                            translateObjectType(entry.getKey()) + " (" + entry.getValue() + ")",
                            entry.getValue()
                    ));
                }
            }
        }
        PieChart chart = new PieChart(pieChartData);
        chart.setLegendVisible(false);
        chart.setLabelsVisible(true);
        chart.setStyle("-fx-font-size: 11px;");
        return chart;
    }

    private BarChart<String, Number> createElementTypeChart(Map<String, Object> stats) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Типы элементов");
        xAxis.setTickLabelRotation(-45);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Количество");
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Количество");
        Map<String, Integer> elementTypes = (Map<String, Integer>) stats.get("element_types");
        if (elementTypes != null) {
            for (Map.Entry<String, Integer> entry : elementTypes.entrySet()) {
                if (entry.getValue() > 0) {
                    series.getData().add(new XYChart.Data<>(
                            translateElementType(entry.getKey()),
                            entry.getValue()
                    ));
                }
            }
        }
        barChart.getData().add(series);
        barChart.setLegendVisible(false);
        barChart.setBarGap(3);
        return barChart;
    }

    private Map<String, Object> loadStatisticsFromDb() {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {
            stats.put("total_objects", getSingleInt(conn, "SELECT COUNT(*) FROM metadata_objects"));
            stats.put("total_modules", getSingleInt(conn, "SELECT COUNT(*) FROM metadata_modules"));
            stats.put("total_functions", getSingleInt(conn, "SELECT COUNT(*) FROM metadata_functions"));
            stats.put("total_elements", getSingleInt(conn, "SELECT COUNT(*) FROM code_elements"));
            String metadataSql = """
                SELECT object_type, COUNT(*) as cnt 
                FROM metadata_objects 
                GROUP BY object_type 
                ORDER BY cnt DESC
                """;
            Map<String, Integer> metadataStats = new HashMap<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(metadataSql)) {
                while (rs.next()) {
                    metadataStats.put(rs.getString("object_type"), rs.getInt("cnt"));
                }
            }
            stats.put("metadata_stats", metadataStats);
            stats.put("object_types", metadataStats);
            stats.put("element_types", getKeyValueMap(conn,
                    "SELECT element_type, COUNT(*) as cnt FROM code_elements GROUP BY element_type ORDER BY cnt DESC"
            ));
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки статистики: " + e.getMessage());
            stats.put("total_objects", 0);
            stats.put("total_modules", 0);
            stats.put("total_functions", 0);
            stats.put("total_elements", 0);
            stats.put("metadata_stats", new HashMap<>());
            stats.put("object_types", new HashMap<>());
            stats.put("element_types", new HashMap<>());
        }
        return stats;
    }

    private Map<String, Integer> getElementTypeCountsFromDb() {
        Map<String, Integer> counts = new HashMap<>();
        String sql = "SELECT element_type, COUNT(*) as cnt FROM code_elements GROUP BY element_type ORDER BY cnt DESC";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                counts.put(rs.getString("element_type"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки типов элементов: " + e.getMessage());
        }
        return counts;
    }

    private int getSingleInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Map<String, Integer> getKeyValueMap(Connection conn, String sql) throws SQLException {
        Map<String, Integer> map = new HashMap<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getInt(2));
            }
        }
        return map;
    }

    private String translateObjectType(String engType) {
        return ConfigTreeManager.getInstance().getRussianNameByObjectType(engType);
    }

    private String translateElementType(String engType) {
        switch (engType) {
            case "ВызовФункции": return "Вызовы функций";
            case "Запрос": return "Запросы";
            case "ЦиклНезависимый": return "Циклы";
            case "ЦиклЗапроса": return "Циклы запросов";
            case "Транзакция": return "Транзакции";
            case "Блокировка": return "Блокировки";
            default: return engType;
        }
    }

    // ИСПРАВЛЕННЫЙ КЛАСС MetadataItem
    public static class MetadataItem {
        private final String englishType;
        private final String russianType;  // переименуем для ясности
        private final int count;
        private final String percent;

        public MetadataItem(String englishType, String russianType, int count, String percent) {
            this.englishType = englishType;
            this.russianType = russianType;  // ← сохраняем русское имя
            this.count = count;
            this.percent = percent;
        }

        public String getEnglishType() { return englishType; }
        public String getRussianType() { return russianType; }  // ← русское имя
        public int getCount() { return count; }
        public String getPercent() { return percent; }

        public javafx.beans.property.SimpleStringProperty typeProperty() {
            return new javafx.beans.property.SimpleStringProperty(russianType);  // ← возвращаем русское!
        }

        public javafx.beans.property.SimpleIntegerProperty countProperty() {
            return new javafx.beans.property.SimpleIntegerProperty(count);
        }

        public javafx.beans.property.SimpleStringProperty percentProperty() {
            return new javafx.beans.property.SimpleStringProperty(percent);
        }
    }


}