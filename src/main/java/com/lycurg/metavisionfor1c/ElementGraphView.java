package com.lycurg.metavisionfor1c;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.application.Platform;
import java.util.*;
import java.sql.*;


//# Визуализатор структуры функции с графом вызовов, иерархией элементов и настраиваемыми стилями
public class ElementGraphView extends BaseGraphView {


    // Кэши для графа
    private static Map<Integer, String> moduleNameCache = new HashMap<>();
    private static Map<Integer, Map<String, Integer>> elementCountCache = new HashMap<>();
    private static boolean cacheLoaded = false;

    // 🔥 КОНСТАНТЫ ПОЗИЦИОНИРОВАНИЯ
    private static final double MAIN_FUNCTION_X = 50;
    private static final double MAIN_FUNCTION_Y = 100;
    private static final double HORIZONTAL_SPACING = 200;
    private static final double VERTICAL_SPACING = 150;

    private double savedScale = 1.0;
    private double savedTranslateX = 0;
    private double savedTranslateY = 0;
    private boolean hasSavedViewport = false;
    private double savedRedSquareScreenX = 0;
    private double savedRedSquareScreenY = 0;

    public class CallerInfo {
        public int callerId;
        public String callerName;
        public String moduleName;
        public int level;
        public int targetId;
        public Integer functionId;

        public CallerInfo(int callerId, String callerName, String moduleName, int level, int targetId, Integer functionId) {
            this.callerId = callerId;
            this.callerName = callerName;
            this.moduleName = moduleName;
            this.level = level;
            this.targetId = targetId;
            this.functionId = functionId;
        }
    }

    private Map<String, GraphNode> nodeMap = new HashMap<>();
    private Set<Rectangle> interactiveElements = new HashSet<>();
    private Map<Integer, Point2D> functionCoordinates = new HashMap<>();
    private Set<String> drawnArrows = new HashSet<>();
    private static Map<Integer, List<CallerInfo>> callGraphCache = new HashMap<>();
    private FunctionClickListener functionClickListener;

    public ElementGraphView() {
        super(); // Вызываем конструктор BaseGraphView
    }

    // ========== ПУБЛИЧНЫЕ МЕТОДЫ ==========

    @Override
    protected void applyGraphStyle() {
        // При изменении стиля перестраиваем граф
        Platform.runLater(() -> {
            // Нужно перестроить граф с новым стилем
            System.out.println("🔄 Применен стиль: " + currentStyle);
        });
    }







    public interface LineNavigationListener {
        void onLineSelected(int lineNumber);
    }

    private LineNavigationListener lineNavigationListener;

    public void setLineNavigationListener(LineNavigationListener listener) {
        this.lineNavigationListener = listener;
    }
    public void setFunctionClickListener(FunctionClickListener listener) {
        this.functionClickListener = listener;
    }




    public static void clearGraphCache() {
        moduleNameCache.clear();
        elementCountCache.clear();
        cacheLoaded = false;
    }


    //построение полного графа (верхние вызовы + внутренние элементы)
    public void buildCompleteGraph(String functionName, List<CodeElement> elements, int mainFunctionId) {
        long startTime = System.currentTimeMillis();

        // Сохраняем позицию для восстановления
        if (!graphPane.getChildren().isEmpty()) {
            Point2D redSquareScreenPos = zoomGroup.localToScene(MAIN_FUNCTION_X, MAIN_FUNCTION_Y);
            savedRedSquareScreenX = redSquareScreenPos.getX();
            savedRedSquareScreenY = redSquareScreenPos.getY();
            savedScale = scale;
            hasSavedViewport = true;
        }

        graphPane.getChildren().clear();
        nodeMap.clear();
        interactiveElements.clear();

        // ========== ЗАГРУЖАЕМ ВСЕ ДАННЫЕ В КЭШ ПЕРЕД ПОСТРОЕНИЕМ ==========
        Set<Integer> allFunctionIds = new HashSet<>();
        allFunctionIds.add(mainFunctionId);

        // Собираем ID из элементов
        for (CodeElement element : elements) {
            if (element.function_id != null && element.function_id > 0) {
                allFunctionIds.add(element.function_id);
            }
            if (element.calledFunctionId != null && element.calledFunctionId > 0) {
                allFunctionIds.add(element.calledFunctionId);
            }
        }

        // Загружаем вызовы верхних уровней и собираем их ID
        List<CallerInfo> upperCallers = executeCallGraphQuery(mainFunctionId);
        for (CallerInfo caller : upperCallers) {
            allFunctionIds.add(caller.callerId);
            allFunctionIds.add(caller.targetId);
        }

        // Загружаем кэш (модули и счетчики элементов) для всех ID одним запросом
        //loadGraphCache(allFunctionIds);
        // ================================================================

        buildUpperCallGraph(mainFunctionId);

        if (elements.isEmpty()) {
            showNoDataMessage();
            return;
        }

        buildMainGraph(functionName, elements, mainFunctionId);
        connectInternalFunctionsToLevels(mainFunctionId, elements, functionName);

        if (hasSavedViewport) {
            Platform.runLater(() -> {
                Point2D newRedSquarePos = zoomGroup.localToScene(MAIN_FUNCTION_X, MAIN_FUNCTION_Y);
                double deltaX = savedRedSquareScreenX - newRedSquarePos.getX();
                double deltaY = savedRedSquareScreenY - newRedSquarePos.getY();
                zoomGroup.setTranslateX(zoomGroup.getTranslateX() + deltaX);
                zoomGroup.setTranslateY(zoomGroup.getTranslateY() + deltaY);
                zoomGroup.setScaleX(savedScale);
                zoomGroup.setScaleY(savedScale);
                scale = savedScale;
            });
        }

        System.out.println("⏱️ Граф построен за " + (System.currentTimeMillis() - startTime) + "ms");
    }

    private void loadGraphCache(Set<Integer> functionIds) {
        if (functionIds.isEmpty()) return;

        // Загружаем модули
        String sqlModules = "SELECT mf.id, mm.object_full_name FROM metadata_functions mf " +
                "JOIN metadata_modules mm ON mf.module_id = mm.id " +
                "WHERE mf.id IN (" + String.join(",", Collections.nCopies(functionIds.size(), "?")) + ")";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sqlModules)) {
            int i = 1;
            for (int id : functionIds) ps.setInt(i++, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                moduleNameCache.put(rs.getInt("id"), rs.getString("object_full_name"));
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки кэша модулей: " + e.getMessage());
        }

        // Загружаем счетчики элементов
        String sqlCounts = "SELECT function_id, element_type, COUNT(*) as cnt FROM code_elements " +
                "WHERE function_id IN (" + String.join(",", Collections.nCopies(functionIds.size(), "?")) + ") " +
                "GROUP BY function_id, element_type";

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sqlCounts)) {
            int i = 1;
            for (int id : functionIds) ps.setInt(i++, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int funcId = rs.getInt("function_id");
                String type = rs.getString("element_type");
                int cnt = rs.getInt("cnt");
                elementCountCache.computeIfAbsent(funcId, k -> new HashMap<>()).put(type, cnt);
            }
        } catch (SQLException e) {
            System.err.println("❌ Ошибка загрузки кэша счетчиков: " + e.getMessage());
        }
    }

    public static void clearCallGraphCache() {
        int cacheSize = callGraphCache.size();
        callGraphCache.clear();
        clearGraphCache(); // ← добавить
        System.out.println("🧹 Очищен кэш графов вызовов: " + cacheSize + " записей");
    }
    // ========== МЕТОДЫ ГРАФА ВЫЗОВОВ ==========


    // визуализация цепочки вызовов функции (3 уровня)
    private void buildUpperCallGraph(int mainFunctionId) {
        if (mainFunctionId <= 0) return;
        functionCoordinates.clear();
        drawnArrows.clear();

        List<CallerInfo> upperCallers = executeCallGraphQuery(mainFunctionId);
        Map<Integer, CallerInfo> uniqueCallers = new HashMap<>();
        for (CallerInfo caller : upperCallers) {
            if (!uniqueCallers.containsKey(caller.callerId)) {
                uniqueCallers.put(caller.callerId, caller);
            }
        }

        List<CallerInfo> finalCallers = new ArrayList<>(uniqueCallers.values());
        Map<Integer, List<CallerInfo>> callersByTarget = new HashMap<>();
        for (CallerInfo caller : finalCallers) {
            callersByTarget.computeIfAbsent(caller.targetId, k -> new ArrayList<>()).add(caller);
        }

        Map<Integer, List<CallerInfo>> callersByLevel = distributeByLevels(mainFunctionId, callersByTarget);

        for (int level = 1; level <= 3; level++) {
            List<CallerInfo> levelCallers = callersByLevel.get(level);
            if (levelCallers != null) {
                double levelY = MAIN_FUNCTION_Y + 100 - (level * VERTICAL_SPACING);
                double levelX = MAIN_FUNCTION_X + 60;
                for (CallerInfo caller : levelCallers) {
                    functionCoordinates.put(caller.callerId, new Point2D(levelX, levelY));
                    createUpperLevelNode(caller, levelX, levelY, level);
                    levelX += HORIZONTAL_SPACING;
                }
            }
        }

        functionCoordinates.put(mainFunctionId, new Point2D(MAIN_FUNCTION_X, MAIN_FUNCTION_Y));
        drawAllArrows(callersByLevel, functionCoordinates, MAIN_FUNCTION_Y);
    }

    // ========== МЕТОДЫ ОСНОВНОГО ГРАФА ==========

    private void buildMainGraph(String functionName, List<CodeElement> elements, int mainFunctionId) {
        GraphNode rootNode = new GraphNode("ROOT", functionName, "ОсновнаяФункция", null);
        rootNode.functionId = mainFunctionId;  // ← УСТАНОВКА
        nodeMap.put("ROOT", rootNode);

        for (CodeElement element : elements) {
            String nodeId = String.valueOf(element.id);
            GraphNode node = new GraphNode(
                    nodeId,
                    element.subtype != null ? element.subtype : element.type,
                    element.type,
                    element
            );

            if ("ВызовФункции".equals(element.type) && element.calledFunctionId != null && element.calledFunctionId > 0) {
                node.functionId = element.calledFunctionId; // шарики вызываемой функции
            } else {
                node.functionId = element.function_id;
            }

            nodeMap.put(nodeId, node);
        }

        buildHierarchy(elements, rootNode);

        if (rootNode.children.isEmpty() && elements.size() > 0) {
            for (CodeElement element : elements) {
                String nodeId = String.valueOf(element.id);
                GraphNode node = nodeMap.get(nodeId);
                if (node != null && !rootNode.children.contains(node)) {
                    rootNode.children.add(node);
                }
            }
        }

        sortChildrenByStartLine(rootNode);
        calculateVerticalTreePositions(rootNode, MAIN_FUNCTION_X, MAIN_FUNCTION_Y, 0);
        drawVerticalConnections(rootNode);
        drawNodes();
    }

    private void buildHierarchy(List<CodeElement> elements, GraphNode rootNode) {
        for (CodeElement element : elements) {
            String nodeId = String.valueOf(element.id);
            GraphNode node = nodeMap.get(nodeId);
            if (node == null) continue;

            String parentId = determineParentId(element);
            GraphNode parent = nodeMap.get(parentId);

            if (parent != null && !parent.id.equals(node.id)) {
                parent.children.add(node);
            } else {
                rootNode.children.add(node);
            }
        }
    }

    private String determineParentId(CodeElement element) {
        if (element.ownerElementId != null && !element.ownerElementId.trim().isEmpty()) {
            String ownerId = element.ownerElementId.trim();
            if (nodeMap.containsKey(ownerId)) return ownerId;
        }
        return "ROOT";
    }

    private void sortChildrenByStartLine(GraphNode node) {
        node.children.sort((a, b) -> {
            int lineA = (a.element != null) ? a.element.startLine : 0;
            int lineB = (b.element != null) ? b.element.startLine : 0;
            return Integer.compare(lineA, lineB);
        });

        for (GraphNode child : node.children) {
            sortChildrenByStartLine(child);
        }
    }

    private double calculateVerticalTreePositions(GraphNode node, double x, double y, int depth) {
        node.x = x;
        node.y = y;

        double currentY = y + 80;        // первый ребёнок чуть ниже родителя
        double childX   = x + 170;       // и правее — отступ по уровню

        for (GraphNode child : node.children) {
            currentY = calculateVerticalTreePositions(child, childX, currentY, depth + 1);
        }

        return Math.max(currentY, y + 80);
    }

    private void drawVerticalConnections(GraphNode node) {
        if ("ROOT".equals(node.id)) {
            for (GraphNode child : node.children) {
                drawVerticalConnections(child);
            }
            return;
        }

        for (GraphNode child : node.children) {
            // Из нижнего-правого угла родителя
            double fromX = node.x + 160;
            double fromY = node.y + 25;
            // В левый край ребёнка
            double toX   = child.x;
            double toY   = child.y + 25;

            // Г-образная линия: сначала вниз, потом вправо
            Line vertical = new Line(fromX, fromY, fromX, toY);
            Line horizontal = new Line(fromX, toY, toX, toY);

            vertical.setStroke(Color.GRAY);
            vertical.setStrokeWidth(1.5);
            vertical.setMouseTransparent(true);

            horizontal.setStroke(Color.GRAY);
            horizontal.setStrokeWidth(1.5);
            horizontal.setMouseTransparent(true);

            // Стрелка на конце
            Line arrowHead1 = new Line(toX, toY, toX - 8, toY - 5);
            Line arrowHead2 = new Line(toX, toY, toX - 8, toY + 5);
            arrowHead1.setStroke(Color.GRAY);
            arrowHead2.setStroke(Color.GRAY);
            arrowHead1.setStrokeWidth(1.5);
            arrowHead2.setStrokeWidth(1.5);

            graphPane.getChildren().addAll(vertical, horizontal, arrowHead1, arrowHead2);
            drawVerticalConnections(child);
        }
    }

    private void drawNodes() {
        for (GraphNode node : nodeMap.values()) {
            if ("ROOT".equals(node.id)) continue;
            createNodeVisual(node);
        }
    }

    // ========== ВИЗУАЛИЗАЦИЯ УЗЛОВ ==========

    private void createUpperLevelNode(CallerInfo caller, double x, double y, int level) {
        Rectangle rect = new Rectangle(x, y, 160, 50);

        // 🔥 ПРИМЕНЯЕМ СТИЛЬ
        switch (currentStyle) {
            case CLASSIC:
                rect.setFill(Color.web("#E3F2FD"));
                rect.setStroke(Color.BLUE);
                rect.setStrokeWidth(1.5);
                rect.setArcWidth(8);
                rect.setArcHeight(8);
                rect.setEffect(null);
                break;

            case MODERN:
                LinearGradient gradient1 = new LinearGradient(
                        0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#E3F2FD").brighter()),
                        new Stop(1, Color.web("#E3F2FD").darker())
                );
                rect.setFill(gradient1);
                rect.setStroke(Color.web("#1976D2"));
                rect.setStrokeWidth(1.8);
                rect.setArcWidth(10);
                rect.setArcHeight(10);
                rect.setEffect(new DropShadow(5, Color.rgb(0, 0, 0, 0.3)));
                break;

            case MINIMALIST:
                rect.setFill(Color.web("#F8FBFF"));
                rect.setStroke(Color.web("#1565C0"));
                rect.setStrokeWidth(1.0);
                rect.setArcWidth(4);
                rect.setArcHeight(4);
                rect.setEffect(new DropShadow(2, Color.rgb(0, 0, 0, 0.1)));
                break;
        }

        graphPane.getChildren().add(rect);

        String moduleInfo = getModuleNameByFunctionId(caller.callerId);
        CodeElement fakeElement = new CodeElement();
        fakeElement.type = "ВызовФункции";
        fakeElement.subtype = caller.callerName;
        fakeElement.startLine = 1;
        fakeElement.text = "Функция уровня " + level;

        String fullInfo = createFullElementTooltip(fakeElement, moduleInfo);
        Tooltip tooltip = new Tooltip(fullInfo);
        Tooltip.install(rect, tooltip);

        // 🔥 АНИМАЦИЯ ТОЛЬКО ДЛЯ MODERN СТИЛЯ
        if (currentStyle == GraphStyle.MODERN) {
            rect.setOnMouseEntered(e -> {
                rect.setStroke(Color.web("#FFD700"));
                rect.setStrokeWidth(2.2);
                rect.setEffect(new InnerShadow(3, Color.rgb(0, 0, 0, 0.3)));
            });

            rect.setOnMouseExited(e -> {
                rect.setStroke(Color.web("#1976D2"));
                rect.setStrokeWidth(1.8);
                rect.setEffect(new DropShadow(5, Color.rgb(0, 0, 0, 0.3)));
            });
        } else {
            rect.setOnMouseEntered(e -> rect.setStroke(Color.YELLOW));
            rect.setOnMouseExited(e -> rect.setStroke(Color.BLUE));
        }

       rect.setOnMouseClicked(e -> {
            System.out.println("🖱️ CLICK: " + caller.callerName);
            if (functionClickListener != null) {
                functionClickListener.onFunctionClicked(caller.callerId, caller.callerName);
            }
            e.consume();
        });


        // 🔥 ИЗМЕНЕНИЕ: ПЕРВАЯ СТРОКА - НАЗВАНИЕ ФУНКЦИИ
        Label nameLabel = new Label(truncateText(caller.callerName, 22));
        nameLabel.setLayoutX(x + 5);
        nameLabel.setLayoutY(y + 5); // ПЕРВАЯ СТРОКА
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        nameLabel.setTextFill(Color.DARKBLUE);
        nameLabel.setMouseTransparent(true);

        // 🔥 ВТОРАЯ СТРОКА - МОДУЛЬ (если есть)
        Label moduleLabel = null;
        if (!moduleInfo.isEmpty()) {
            moduleLabel = new Label(truncateText(moduleInfo, 22));
            moduleLabel.setLayoutX(x + 5);
            moduleLabel.setLayoutY(y + 20); // ВТОРАЯ СТРОКА
            moduleLabel.setFont(Font.font("Arial", 8));
            moduleLabel.setTextFill(Color.DARKGRAY);
            moduleLabel.setMouseTransparent(true);
        }

        // 🔥 ТРЕТЬЯ СТРОКА - УРОВЕНЬ
        Label levelLabel = new Label("Уровень " + level);
        levelLabel.setLayoutX(x + 5);
        levelLabel.setLayoutY(moduleInfo.isEmpty() ? y + 20 : y + 35); // Зависит от наличия модуля
        levelLabel.setFont(Font.font("Arial", 7));
        levelLabel.setTextFill(Color.DARKGRAY);
        levelLabel.setMouseTransparent(true);

        // Добавляем все лейблы
        graphPane.getChildren().add(nameLabel);
        if (moduleLabel != null) {
            graphPane.getChildren().add(moduleLabel);
        }
        graphPane.getChildren().add(levelLabel);

        GraphNode node = new GraphNode(
                String.valueOf(caller.callerId),
                caller.callerName,
                "ВызовФункции",
                null
        );
        node.functionId = caller.callerId;
        addElementIndicators(node, x, y);
    }


    //отрисовка узлов с индикаторами элементов и тултипами
    private void createNodeVisual(GraphNode node) {
        Rectangle rect = new Rectangle(node.x, node.y, 160, 50);

        Color baseColor = ElementColors.getColorForType(node.type);

        switch (currentStyle) {
            case CLASSIC:
                rect.setFill(baseColor);
                rect.setStroke(Color.BLACK);
                rect.setStrokeWidth(1);
                rect.setArcWidth(8);
                rect.setArcHeight(8);
                rect.setEffect(null);
                break;

            case MODERN:
                LinearGradient gradient = new LinearGradient(
                        0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, baseColor.brighter()),
                        new Stop(0.5, baseColor),
                        new Stop(1, baseColor.darker())
                );
                rect.setFill(gradient);

                if ("ОсновнаяФункция".equals(node.type)) {
                    rect.setStroke(Color.web("#B71C1C"));
                    rect.setStrokeWidth(2.5);
                } else if (node.type.contains("Цикл")) {
                    rect.setStroke(Color.web("#1B5E20"));
                    rect.setStrokeWidth(2.2);
                } else if ("Запрос".equals(node.type)) {
                    rect.setStroke(Color.web("#FF6F00"));
                    rect.setStrokeWidth(2.2);
                } else {
                    rect.setStroke(Color.web("#0D47A1"));
                    rect.setStrokeWidth(1.8);
                }

                rect.setArcWidth(12);
                rect.setArcHeight(12);
                rect.setEffect(new DropShadow(7, Color.rgb(0, 0, 0, 0.4)));
                break;

            case MINIMALIST:
                rect.setFill(baseColor.deriveColor(0, 1.0, 1.0, 0.9));
                rect.setStroke(Color.web("#424242"));
                rect.setStrokeWidth(0.8);
                rect.setArcWidth(4);
                rect.setArcHeight(4);
                rect.setEffect(new DropShadow(3, Color.rgb(0, 0, 0, 0.2)));
                break;
        }

        if (currentStyle == GraphStyle.MODERN) {
            rect.setOnMousePressed(e -> rect.setTranslateY(2));
            rect.setOnMouseReleased(e -> rect.setTranslateY(0));
        }

        rect.setMouseTransparent(false);
        interactiveElements.add(rect);

        String moduleInfo = "";
        String elementText = node.label;

        if (node.functionId != null) {
            moduleInfo = getModuleNameByFunctionId(node.functionId);
        } else if (node.element != null && node.element.function_id != null) {
            moduleInfo = getModuleNameByFunctionId(node.element.function_id);
            if (node.element.text != null && !node.element.text.trim().isEmpty()) {
                elementText = node.element.text.trim();
            }
        }

        List<Label> labels = new ArrayList<>();

        // 🔥 НАЗВАНИЕ ФУНКЦИИ НА ПЕРВОЙ СТРОКЕ
        Label nameLabel = new Label(truncateText(node.label, 22));
        nameLabel.setLayoutX(node.x + 5);
        nameLabel.setLayoutY(node.y + 5); // ПЕРВАЯ СТРОКА
        nameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        nameLabel.setMaxWidth(150);
        nameLabel.setMouseTransparent(true);

        if ("ОсновнаяФункция".equals(node.type)) {
            nameLabel.setTextFill(Color.WHITE);
        } else if (node.type.contains("Цикл")) {
            nameLabel.setTextFill(Color.BLACK);
        } else {
            nameLabel.setTextFill(Color.DARKBLUE);
        }

        labels.add(nameLabel);

        // 🔥 МОДУЛЬ НА ВТОРОЙ СТРОКЕ
        if (!moduleInfo.isEmpty()) {
            Label moduleLabel = new Label(truncateText(moduleInfo, 22));
            moduleLabel.setLayoutX(node.x + 5);
            moduleLabel.setLayoutY(node.y + 20); // ВТОРАЯ СТРОКА
            moduleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 9));
            moduleLabel.setTextFill(Color.DARKBLUE);
            moduleLabel.setMaxWidth(150);
            moduleLabel.setMouseTransparent(true);
            labels.add(moduleLabel);
        }

        // Текст элемента
        Label textLabel = new Label(truncateText(elementText, 30));
        textLabel.setLayoutX(node.x + 5);
        textLabel.setLayoutY(node.y + 35);
        textLabel.setFont(Font.font("Arial", 7));

        if ("ОсновнаяФункция".equals(node.type)) {
            textLabel.setTextFill(Color.WHITE);
        } else if (node.type.contains("Цикл")) {
            textLabel.setTextFill(Color.BLACK);
        } else {
            textLabel.setTextFill(Color.DARKGRAY);
        }

        textLabel.setMaxWidth(150);
        textLabel.setMouseTransparent(true);
        labels.add(textLabel);

        graphPane.getChildren().addAll(rect);
        graphPane.getChildren().addAll(labels);

        if ("ВызовФункции".equals(node.type) || "ОсновнаяФункция".equals(node.type)) {
            addElementIndicators(node, node.x, node.y);
        }

        if (node.element != null) {
            String fullInfo = createFullElementTooltip(node.element, moduleInfo);
            Tooltip tooltip = new Tooltip(fullInfo);
            Tooltip.install(rect, tooltip);
        }

        if (node.element != null) {
            setupElementInteractivity(rect, node.element);
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private void addElementIndicators(GraphNode node, double x, double y) {
        if (!"ВызовФункции".equals(node.type) && !"ОсновнаяФункция".equals(node.type)) return;

        Map<String, Integer> elementCounts = countElementsInFunction(node);
        if (elementCounts.isEmpty()) return;

        String[] priorityOrder = {"Блокировка", "Транзакция", "ВызовФункции", "Запрос", "ЦиклНезависимый", "ЦиклЗапроса"};
        double startX = x + 155;
        double indicatorY = y - 2;
        double spacing = 8;
        int indicatorsAdded = 0;

        for (String elementType : priorityOrder) {
            if (elementCounts.getOrDefault(elementType, 0) > 0) {
                Circle indicator = new Circle(startX, indicatorY, currentStyle == GraphStyle.MODERN ? 5 : 4);

                // 🔥 РАЗНЫЕ СТИЛИ ДЛЯ ИНДИКАТОРОВ
                if (currentStyle == GraphStyle.MODERN) {
                    Color baseColor = ElementColors.getColorForType(elementType);
                    RadialGradient gradient = new RadialGradient(
                            0, 0, 0.5, 0.5, 0.5, true,
                            CycleMethod.NO_CYCLE,
                            new Stop(0, baseColor.brighter()),
                            new Stop(1, baseColor.darker())
                    );
                    indicator.setFill(gradient);
                    indicator.setStroke(Color.BLACK);
                    indicator.setStrokeWidth(0.7);
                    indicator.setEffect(new DropShadow(3, Color.rgb(0, 0, 0, 0.5)));
                } else {
                    // Классический и минималистичный
                    indicator.setFill(ElementColors.getColorForType(elementType));
                    indicator.setStroke(Color.BLACK);
                    indicator.setStrokeWidth(0.5);
                }

                int count = elementCounts.get(elementType);
                Tooltip tooltip = new Tooltip(elementType + ": " + count + " шт");
                Tooltip.install(indicator, tooltip);

                graphPane.getChildren().add(indicator);
                startX -= spacing;
                indicatorsAdded++;
                if (indicatorsAdded >= 5) break;
            }
        }
    }

    private Map<String, Integer> countElementsInFunction(GraphNode node) {
        Integer functionId = node.functionId;
        if (functionId == null && node.element != null) {
            functionId = node.element.function_id;
        }
        if (functionId == null || functionId <= 0) return new HashMap<>();

        // Берём из кэша — не идём в БД
        return elementCountCache.getOrDefault(functionId, new HashMap<>());
    }



    private void setupElementInteractivity(Rectangle rect, CodeElement element) {
        if (currentStyle == GraphStyle.MODERN) {
            rect.setOnMouseEntered(e -> {
                rect.setStroke(Color.web("#FFD700"));
                rect.setStrokeWidth(2.2);
            });

            rect.setOnMouseExited(e -> {
                if ("ОсновнаяФункция".equals(element.type)) {
                    rect.setStroke(Color.web("#B71C1C"));
                } else if (element.type.contains("Цикл")) {
                    rect.setStroke(Color.web("#1B5E20"));
                } else if ("Запрос".equals(element.type)) {
                    rect.setStroke(Color.web("#FF6F00"));
                } else {
                    rect.setStroke(Color.web("#0D47A1"));
                }
                rect.setStrokeWidth(1.8);
            });
        } else {
            rect.setOnMouseEntered(e -> {
                rect.setStroke(Color.YELLOW);
                rect.setStrokeWidth(2);
            });

            rect.setOnMouseExited(e -> {
                rect.setStroke(Color.BLACK);
                rect.setStrokeWidth(1);
            });
        }

        rect.setOnMouseClicked(e -> {
            System.out.println("🖱️ CLICKED: " + element.type + " (line " + element.startLine + ")");

            if ("ВызовФункции".equals(element.type)) {
                if (element.calledFunctionId != null && element.calledFunctionId > 0) {
                    if (functionClickListener != null) {
                        functionClickListener.onFunctionClicked(element.calledFunctionId, element.subtype);
                    }
                }
            } else {
                // Цикл, Запрос и всё остальное — скроллим к строке
                if (lineNavigationListener != null && element.startLine > 0) {
                    lineNavigationListener.onLineSelected(element.startLine);
                }
            }
            e.consume();
        });

    }

    // ========== МЕТОДЫ СТРЕЛОК ==========


    //рисование стрелок-зависимостей между функциями
    private void drawAllArrows(Map<Integer, List<CallerInfo>> callersByLevel,
                               Map<Integer, Point2D> functionCoordinates, double baseY) {
        Point2D mainFunctionPoint = functionCoordinates.entrySet().stream()
                .filter(e -> e.getValue().getY() == baseY)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new Point2D(MAIN_FUNCTION_X, baseY));

        for (int level = 1; level <= 3; level++) {
            List<CallerInfo> levelCallers = callersByLevel.get(level);
            if (levelCallers != null) {
                for (CallerInfo caller : levelCallers) {
                    Point2D fromPoint = functionCoordinates.get(caller.callerId);
                    if (fromPoint == null) continue;

                    double fromX = fromPoint.getX() + 80;
                    double fromY = fromPoint.getY() + 50;
                    Point2D toPoint;

                    if (level == 1) {
                        toPoint = mainFunctionPoint;
                    } else {
                        toPoint = functionCoordinates.get(caller.targetId);
                        if (toPoint == null) continue; // ⭐ ИСПРАВЛЕНО: continue если НЕТ toPoint
                    }

                    if (toPoint != null) {
                        double toX = (level == 1) ? toPoint.getX() + 250 : toPoint.getX() + 80;
                        double toY = (level == 1) ? toPoint.getY() + 100 : toPoint.getY();
                        drawSimpleArrow(fromX, fromY, toX, toY, level);
                    }
                }
            }
        }
    }
    private void drawSimpleArrow(double fromX, double fromY, double toX, double toY, int level) {
        String arrowKey = fromX + "," + fromY + "->" + toX + "," + toY;
        if (drawnArrows.contains(arrowKey)) return;
        drawnArrows.add(arrowKey);

        Line arrowLine = new Line(fromX, fromY, toX, toY);
        Color strokeColor = switch (level) {
            case 1 -> Color.web("#1976D2");
            case 2 -> Color.web("#388E3C");
            case 3 -> Color.web("#F57C00");
            default -> Color.web("#757575");
        };

        arrowLine.setStroke(strokeColor);
        arrowLine.setStrokeWidth(1.8);
        arrowLine.getStrokeDashArray().addAll(5d, 5d);

        // 🔥 ТЕНЬ ДЛЯ ЛИНИЙ
        arrowLine.setEffect(new DropShadow(2, Color.rgb(0, 0, 0, 0.15)));

        double angle = Math.atan2(toY - fromY, toX - fromX);
        double arrowLength = 10;

        Line arrowHead1 = new Line(
                toX, toY,
                toX - arrowLength * Math.cos(angle - Math.PI/6),
                toY - arrowLength * Math.sin(angle - Math.PI/6)
        );

        Line arrowHead2 = new Line(
                toX, toY,
                toX - arrowLength * Math.cos(angle + Math.PI/6),
                toY - arrowLength * Math.sin(angle + Math.PI/6)
        );

        arrowHead1.setStroke(strokeColor);
        arrowHead2.setStroke(strokeColor);
        arrowHead1.setStrokeWidth(1.8);
        arrowHead2.setStrokeWidth(1.8);

        graphPane.getChildren().addAll(arrowLine, arrowHead1, arrowHead2);
    }

    private void drawArrowBetweenPoints(Point2D fromPoint, Point2D toPoint, int level) {
        double fromX = fromPoint.getX();
        double fromY = fromPoint.getY();
        double toX, toY;

        if (level == 0) {
            toX = MAIN_FUNCTION_X + 160*2 + 30;
            toY = MAIN_FUNCTION_Y + 25*2 + 80;
        } else {
            toX = toPoint.getX() + 160;
            toY = toPoint.getY() + 25;
        }

        String arrowKey = fromX + "," + fromY + "->" + toX + "," + toY;
        if (drawnArrows.contains(arrowKey)) return;
        drawnArrows.add(arrowKey);

        CubicCurve curve = new CubicCurve();
        curve.setStartX(fromX);
        curve.setStartY(fromY);
        curve.setEndX(toX);
        curve.setEndY(toY);

        double controlX1 = fromX + (toX - fromX) * 0.3;
        double controlY1 = fromY;
        double controlX2 = fromX + (toX - fromX) * 0.7;
        double controlY2 = toY;

        curve.setControlX1(controlX1);
        curve.setControlY1(controlY1);
        curve.setControlX2(controlX2);
        curve.setControlY2(controlY2);

        // 🔥 ЦВЕТА С ГРАДИЕНТОМ
        Color strokeColor = switch (level) {
            case 0 -> Color.web("#D32F2F"); // темно-красный
            case 1 -> Color.web("#1976D2"); // темно-синий
            case 2 -> Color.web("#388E3C"); // темно-зеленый
            case 3 -> Color.web("#F57C00"); // темно-оранжевый
            default -> Color.web("#757575");
        };

        curve.setStroke(strokeColor);
        curve.setStrokeWidth(2.5);
        curve.setFill(Color.TRANSPARENT);
        curve.getStrokeDashArray().addAll(3.0, 3.0);

        // 🔥 ТЕНЬ ДЛЯ СТРЕЛКИ
        curve.setEffect(new DropShadow(3, Color.rgb(0, 0, 0, 0.2)));

        double angle = Math.atan2(toY - controlY2, toX - controlX2);
        double arrowLength = 12;

        Line arrowHead1 = new Line(
                toX, toY,
                toX - arrowLength * Math.cos(angle - Math.PI/6),
                toY - arrowLength * Math.sin(angle - Math.PI/6)
        );

        Line arrowHead2 = new Line(
                toX, toY,
                toX - arrowLength * Math.cos(angle + Math.PI/6),
                toY - arrowLength * Math.sin(angle + Math.PI/6)
        );

        arrowHead1.setStroke(strokeColor);
        arrowHead2.setStroke(strokeColor);
        arrowHead1.setStrokeWidth(2.5);
        arrowHead2.setStrokeWidth(2.5);

        graphPane.getChildren().addAll(curve, arrowHead1, arrowHead2);
    }

    // ========== МЕТОДЫ РАБОТЫ С БАЗОЙ ДАННЫХ ==========

    private String getModuleNameByFunctionId(Integer functionId) {
        if (functionId == null) return "";
        return moduleNameCache.getOrDefault(functionId, "");
    }

    private Integer findFunctionIdByElementId(Integer elementId) {
        if (elementId == null) return null;
        String sql = "SELECT function_id FROM code_elements WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, elementId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("function_id");
        } catch (SQLException e) {
            System.err.println("❌ Ошибка поиска function_id: " + e.getMessage());
        }
        return null;
    }

    private List<CallerInfo> executeCallGraphQuery(int mainFunctionId) {
        if (callGraphCache.containsKey(mainFunctionId)) {
            return new ArrayList<>(callGraphCache.get(mainFunctionId));
        }

        String sql = """
            SELECT DISTINCT 
                ce.function_id as caller_id,
                mf.function_name as caller_name, 
                ce.called_function_id as target_id
            FROM code_elements ce
            JOIN metadata_functions mf ON ce.function_id = mf.id
            WHERE ce.element_type = 'ВызовФункции'
              AND ce.called_function_id = ?
              AND ce.function_id != ?
               LIMIT 100
            """;

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mainFunctionId);
            ps.setInt(2, mainFunctionId);
            ResultSet rs = ps.executeQuery();
            List<CallerInfo> allCallers = new ArrayList<>();

            while (rs.next()) {
                CallerInfo info = new CallerInfo(
                        rs.getInt("caller_id"),
                        rs.getString("caller_name"),
                        "",
                        1,
                        rs.getInt("target_id"),
                        rs.getInt("caller_id")
                );
                allCallers.add(info);
            }

            List<CallerInfo> result = computeLevels2and3(mainFunctionId, allCallers);
            callGraphCache.put(mainFunctionId, new ArrayList<>(result));
            return result;

        } catch (SQLException e) {
            System.err.println("❌ Ошибка запроса графа вызовов: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<CallerInfo> computeLevels2and3(int mainFunctionId, List<CallerInfo> level1Callers) {
        if (level1Callers.isEmpty()) return level1Callers;
        String placeholders = String.join(",", Collections.nCopies(level1Callers.size(), "?"));
        String sql = String.format("""
            SELECT DISTINCT 
                ce.function_id as caller_id,
                mf.function_name as caller_name, 
                ce.called_function_id as target_id
            FROM code_elements ce
            JOIN metadata_functions mf ON ce.function_id = mf.id
            WHERE ce.element_type = 'ВызовФункции'
              AND ce.called_function_id IN (%s)
              AND ce.function_id != ?
              LIMIT 100
            """, placeholders);

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (CallerInfo caller : level1Callers) {
                ps.setInt(paramIndex++, caller.callerId);
            }
            ps.setInt(paramIndex, mainFunctionId);
            ResultSet rs = ps.executeQuery();
            List<CallerInfo> level2Callers = new ArrayList<>();

            while (rs.next()) {
                CallerInfo info = new CallerInfo(
                        rs.getInt("caller_id"),
                        rs.getString("caller_name"),
                        "",
                        2,
                        rs.getInt("target_id"),
                        rs.getInt("caller_id")
                );
                level2Callers.add(info);
            }

            List<CallerInfo> level3Callers = computeLevel3(level2Callers, mainFunctionId);
            List<CallerInfo> result = new ArrayList<>();
            result.addAll(level1Callers);
            result.addAll(level2Callers);
            result.addAll(level3Callers);
            return result;

        } catch (SQLException e) {
            System.err.println("❌ Ошибка вычисления уровней 2-3: " + e.getMessage());
            return level1Callers;
        }
    }

    private List<CallerInfo> computeLevel3(List<CallerInfo> level2Callers, int mainFunctionId) {
        if (level2Callers.isEmpty()) return new ArrayList<>();
        String placeholders = String.join(",", Collections.nCopies(level2Callers.size(), "?"));
        String sql = String.format("""
            SELECT DISTINCT 
                ce.function_id as caller_id,
                mf.function_name as caller_name, 
                ce.called_function_id as target_id
            FROM code_elements ce
            JOIN metadata_functions mf ON ce.function_id = mf.id
            WHERE ce.element_type = 'ВызовФункции'
              AND ce.called_function_id IN (%s)
              AND ce.function_id != ?
              LIMIT 100
            """, placeholders);

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (CallerInfo caller : level2Callers) {
                ps.setInt(paramIndex++, caller.callerId);
            }
            ps.setInt(paramIndex, mainFunctionId);
            ResultSet rs = ps.executeQuery();
            List<CallerInfo> level3Callers = new ArrayList<>();

            while (rs.next()) {
                CallerInfo info = new CallerInfo(
                        rs.getInt("caller_id"),
                        rs.getString("caller_name"),
                        "",
                        3,
                        rs.getInt("target_id"),
                        rs.getInt("caller_id")
                );
                level3Callers.add(info);
            }
            return level3Callers;
        } catch (SQLException e) {
            System.err.println("❌ Ошибка вычисления уровня 3: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<Integer, List<CallerInfo>> distributeByLevels(int mainFunctionId, Map<Integer, List<CallerInfo>> callersByTarget) {
        Map<Integer, List<CallerInfo>> callersByLevel = new HashMap<>();
        List<CallerInfo> level1 = callersByTarget.get(mainFunctionId);

        if (level1 != null) {
            callersByLevel.put(1, level1);
            List<CallerInfo> level2 = new ArrayList<>();

            for (CallerInfo level1Caller : level1) {
                List<CallerInfo> callersOfLevel1 = callersByTarget.get(level1Caller.callerId);
                if (callersOfLevel1 != null) {
                    for (CallerInfo caller : callersOfLevel1) {
                        if (!level1.contains(caller)) level2.add(caller);
                    }
                }
            }

            if (!level2.isEmpty()) {
                callersByLevel.put(2, level2);
                List<CallerInfo> level3 = new ArrayList<>();

                for (CallerInfo level2Caller : level2) {
                    List<CallerInfo> callersOfLevel2 = callersByTarget.get(level2Caller.callerId);
                    if (callersOfLevel2 != null) {
                        for (CallerInfo caller : callersOfLevel2) {
                            if (!level1.contains(caller) && !level2.contains(caller)) level3.add(caller);
                        }
                    }
                }

                if (!level3.isEmpty()) callersByLevel.put(3, level3);
            }
        }
        return callersByLevel;
    }

    private void connectInternalFunctionsToLevels(int mainFunctionId, List<CodeElement> elements, String mainFunctionName) {
        Set<Integer> allFunctionIds = new HashSet<>();
        allFunctionIds.add(mainFunctionId);
        List<CallerInfo> allLevelFunctions = executeCallGraphQuery(mainFunctionId);
        for (CallerInfo caller : allLevelFunctions) {
            allFunctionIds.add(caller.callerId);
        }

        String placeholders = String.join(",", Collections.nCopies(allFunctionIds.size(), "?"));
        String sql = String.format("""
            SELECT id, function_id, called_function_id, element_name, start_line, element_text
            FROM code_elements 
            WHERE element_type = 'ВызовФункции' 
              AND function_id IN (%s)
              AND called_function_id IS NOT NULL
            """, placeholders);

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (Integer functionId : allFunctionIds) {
                ps.setInt(paramIndex++, functionId);
            }
            ResultSet rs = ps.executeQuery();
            Map<Integer, List<CodeElement>> callsByFunction = new HashMap<>();

            while (rs.next()) {
                CodeElement call = new CodeElement();
                call.id = rs.getInt("id");
                call.function_id = rs.getInt("function_id");
                call.calledFunctionId = rs.getInt("called_function_id");
                call.subtype = rs.getString("element_name");
                call.startLine = rs.getInt("start_line");
                call.text = rs.getString("element_text");
                call.type = "ВызовФункции";
                callsByFunction.computeIfAbsent(call.function_id, k -> new ArrayList<>()).add(call);
            }

            fastConnectCallsToLevels(callsByFunction, allLevelFunctions, mainFunctionId, mainFunctionName);
        } catch (SQLException e) {
            System.err.println("❌ Ошибка поиска вложенных вызовов: " + e.getMessage());
        }
    }

    private void fastConnectCallsToLevels(Map<Integer, List<CodeElement>> callsByFunction,
                                          List<CallerInfo> allLevelFunctions,
                                          int mainFunctionId, String mainFunctionName) {
        int connectionsFound = 0;
        allLevelFunctions.add(new CallerInfo(mainFunctionId, mainFunctionName, "", 0, mainFunctionId, mainFunctionId));

        for (Map.Entry<Integer, List<CodeElement>> entry : callsByFunction.entrySet()) {
            for (CodeElement call : entry.getValue()) {
                for (CallerInfo levelFunc : allLevelFunctions) {
                    if (call.calledFunctionId.equals(levelFunc.callerId)) {
                        Point2D internalFuncPoint = findElementPoint(call);
                        Point2D levelFuncPoint = functionCoordinates.get(levelFunc.callerId);
                        if (internalFuncPoint != null && levelFuncPoint != null) {
                            drawArrowBetweenPoints(internalFuncPoint, levelFuncPoint, levelFunc.level);
                            connectionsFound++;
                        }
                    }
                }
            }
        }
    }

    private Point2D findElementPoint(CodeElement element) {
        for (GraphNode node : nodeMap.values()) {
            if ("ROOT".equals(node.id)) continue;
            if (node.element != null && node.element.id != null && node.element.id.equals(element.id)) {
                return new Point2D(node.x + 160, node.y + 25);
            }
        }
        return null;
    }

    private String createFullElementTooltip(CodeElement element, String moduleInfo) {
        StringBuilder tooltip = new StringBuilder();
        if (!moduleInfo.isEmpty()) tooltip.append("📁 Модуль: ").append(moduleInfo).append("\n\n");
        tooltip.append("🎯 Тип: ").append(element.type).append("\n");
        if (element.subtype != null && !element.subtype.trim().isEmpty()) {
            tooltip.append("🏷️ Название: ").append(element.subtype).append("\n");
        }
        if (element.startLine > 0) {
            tooltip.append("📏 Строка: ").append(element.startLine);
            if (element.endLine > 0 && element.endLine != element.startLine) {
                tooltip.append(" - ").append(element.endLine);
            }
            tooltip.append("\n\n");
        }
        if (element.text != null && !element.text.trim().isEmpty()) {
            tooltip.append("📝 Полный текст:\n").append(element.text.trim());
        } else {
            tooltip.append("📝 Текст: отсутствует");
        }
        return tooltip.toString();
    }


    public void preloadGraphData(int mainFunctionId, List<CodeElement> elements) {
        Set<Integer> allFunctionIds = new HashSet<>();
        allFunctionIds.add(mainFunctionId);

        for (CodeElement element : elements) {
            if (element.function_id != null && element.function_id > 0)
                allFunctionIds.add(element.function_id);
            if (element.calledFunctionId != null && element.calledFunctionId > 0)
                allFunctionIds.add(element.calledFunctionId);
        }

        List<CallerInfo> upperCallers = executeCallGraphQuery(mainFunctionId);
        for (CallerInfo caller : upperCallers) {
            allFunctionIds.add(caller.callerId);
            allFunctionIds.add(caller.targetId);
        }

        // Прогружаем данные для connectInternalFunctionsToLevels
        for (CallerInfo caller : upperCallers) {
            allFunctionIds.add(caller.callerId);
            // Запускаем вложенные запросы заранее — они попадут в callGraphCache
            executeCallGraphQuery(caller.callerId);
        }

        loadGraphCache(allFunctionIds);
    }


    private void showNoDataMessage() {
        Label noDataLabel = new Label("Нет данных для отображения");
        noDataLabel.setLayoutX(400);
        noDataLabel.setLayoutY(300);
        noDataLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: gray;");
        graphPane.getChildren().add(noDataLabel);
    }

    // ========== ВНУТРЕННИЕ КЛАССЫ ==========

    private static class GraphNode {
        String id;
        String label;
        String type;
        CodeElement element;
        double x, y;
        Integer functionId;
        List<GraphNode> children = new ArrayList<>();

        GraphNode(String id, String label, String type, CodeElement element) {
            this.id = id;
            this.label = label;
            this.type = type;
            this.element = element;
        }
    }

    public interface FunctionClickListener {
        void onFunctionClicked(int functionId, String functionName);
    }
}