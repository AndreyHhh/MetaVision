package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

import java.sql.*;
import java.util.*;

public class NestedElementsGraphView extends BaseGraphView {

    private static final Color DIRECT_CALL_COLOR = Color.web("#4CAF50");
    private static final double CENTER_X = 500;
    private static final double CENTER_Y = 400;
    private static final double VERTICAL_SPACING = 120;
    private static final double HORIZONTAL_SPACING = 150;

    private Scanner_NestedElements.NestedElementIssue currentIssue;
    private final Map<Integer, Shape> nodeMap = new HashMap<>();

    public NestedElementsGraphView() {
        super();
        setGraphStyle(GraphStyle.CLASSIC);
        applyGraphStyle();
    }

    public void buildNestedElementsGraph(Scanner_NestedElements.NestedElementIssue issue) {
        System.out.println("🎨 buildNestedElementsGraph вызван");


        clear();
        nodeMap.clear();
        currentIssue = issue;

        if (issue == null) {
            showNoDataMessage();
            return;
        }

        try {
            List<NestedNode> chain = buildFullCallChain(issue);
            if (chain.isEmpty()) {
                showNoDataMessage();
                return;
            }



            // ЗАМЕНА: передаём parentMap
            layoutAndDrawChain(chain, issue.chainParentMap);

            // Стандартная позиция как в первом графе
   /*         getGraphPane().setTranslateX(-500);
            getGraphPane().setTranslateY(-650);
            getGraphPane().setScaleX(0.6);
            getGraphPane().setScaleY(0.6);
*/
            System.out.println("✅ Граф отрисован");

        } catch (Exception e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            showErrorMessage("Ошибка построения графа");
        }
    }

    @Override
    public void resetZoom() {
        super.resetZoom();
/*        getGraphPane().setTranslateY(-650);
        getGraphPane().setTranslateX(-500);
        getGraphPane().setScaleX(0.6);
        getGraphPane().setScaleY(0.6);*/
    }

    // ЗАМЕНА: полная замена layoutAndDrawChain
    private void layoutAndDrawChain(List<NestedNode> chain, Map<Integer, Integer> parentMap) {
        if (chain.isEmpty()) return;

        Map<Integer, NestedNode> byId = new LinkedHashMap<>();
        for (NestedNode n : chain) byId.put(n.elementId, n);

        // children map
        Map<Integer, List<Integer>> childrenOf = new LinkedHashMap<>();
        for (NestedNode n : chain) childrenOf.put(n.elementId, new ArrayList<>());

        int rootId = chain.get(0).elementId;
        for (NestedNode n : chain) {
            int pid = parentMap.getOrDefault(n.elementId, 0);
            if (pid != 0 && byId.containsKey(pid))
                childrenOf.get(pid).add(n.elementId);
        }

        // Расставляем позиции
        Map<Integer, double[]> positions = new LinkedHashMap<>();
        int[] yCounter = {0};
        assignPositionsDFS(rootId, 0, byId, childrenOf, positions, yCounter);

        // Находим центр (координаты корня)
        double[] rootPos = positions.get(rootId);
        double offsetX = CENTER_X - rootPos[0];
        double offsetY = CENTER_Y - rootPos[1];

        // Рисуем узлы со смещением
        for (Map.Entry<Integer, double[]> e : positions.entrySet()) {
            NestedNode node = byId.get(e.getKey());
            if (node == null) continue;
            double x = e.getValue()[0] + offsetX;
            double y = e.getValue()[1] + offsetY;
            Shape shape = createNestedNode(node, x, y);
            nodeMap.put(node.elementId, shape);
        }

        // Рисуем рёбра
        for (NestedNode node : chain) {
            int pid = parentMap.getOrDefault(node.elementId, 0);
            if (pid == 0) continue;
            Shape parentShape = nodeMap.get(pid);
            Shape childShape  = nodeMap.get(node.elementId);
            if (parentShape != null && childShape != null)
                createBezierConnection(parentShape, childShape, node.nodeType == NodeType.CYCLE);
        }



        centerAndFitGraph();
    }


    public void centerAndFitGraph() {
        Platform.runLater(() -> Platform.runLater(() -> {
            if (nodeMap.isEmpty()) return;

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

            for (Shape shape : nodeMap.values()) {
                var b = shape.getBoundsInParent();
                minX = Math.min(minX, b.getMinX());
                minY = Math.min(minY, b.getMinY());
                maxX = Math.max(maxX, b.getMaxX());
                maxY = Math.max(maxY, b.getMaxY());
            }

            double graphW = maxX - minX;
            double graphH = maxY - minY;

            double viewW = getGraphPane().getParent().getBoundsInLocal().getWidth();
            double viewH = getGraphPane().getParent().getBoundsInLocal().getHeight();

            double scale = Math.min(viewW / (graphW + 100), viewH / (graphH + 100));
            scale = Math.min(scale, 1.0);

            double centerX = (minX + maxX) / 2.0;
            double centerY = (minY + maxY) / 2.0;

            getGraphPane().setScaleX(scale);
            getGraphPane().setScaleY(scale);
            getGraphPane().setTranslateX(viewW / 2.0 - centerX * scale - 500);
            getGraphPane().setTranslateY(viewH / 2.0 - centerY * scale - 650);
        }));
    }


    private void assignPositionsDFS(int nodeId, int depth,
                                    Map<Integer, NestedNode> byId,
                                    Map<Integer, List<Integer>> childrenOf,
                                    Map<Integer, double[]> positions,
                                    int[] yCounter) {
        double x = CENTER_X + depth * 120.0;
        double y = CENTER_Y + yCounter[0] * VERTICAL_SPACING;
        positions.put(nodeId, new double[]{x, y});
        yCounter[0]++;

        // сначала не-функции, потом функции
        List<Integer> children = new ArrayList<>(childrenOf.getOrDefault(nodeId, Collections.emptyList()));
        children.sort((a, b) -> {
            NestedNode na = byId.get(a);
            NestedNode nb = byId.get(b);
            boolean aIsFunc = na != null && (na.nodeType == NodeType.FUNCTION || na.nodeType == NodeType.RECURSIVE);
            boolean bIsFunc = nb != null && (nb.nodeType == NodeType.FUNCTION || nb.nodeType == NodeType.RECURSIVE);
            return Boolean.compare(aIsFunc, bIsFunc);
        });

        for (int childId : children) {
            NestedNode child = byId.get(childId);
            boolean isFunction = child != null &&
                    (child.nodeType == NodeType.FUNCTION || child.nodeType == NodeType.RECURSIVE);
            int childDepth = isFunction ? depth : depth + 1;
            assignPositionsDFS(childId, childDepth, byId, childrenOf, positions, yCounter);
        }
    }

    private void calcTreeLayout(int nodeId, int depth,
                                Map<Integer, List<Integer>> childrenOf,
                                Map<Integer, Double> xPos,
                                Map<Integer, Integer> depthOf,
                                int[] leafCounter) {
        depthOf.put(nodeId, depth);
        List<Integer> children = childrenOf.getOrDefault(nodeId, Collections.emptyList());

        if (children.isEmpty()) {
            xPos.put(nodeId, (double) leafCounter[0]);
            leafCounter[0]++;
        } else {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            for (int childId : children) {
                calcTreeLayout(childId, depth + 1, childrenOf, xPos, depthOf, leafCounter);
                double cx = xPos.get(childId);
                if (cx < minX) minX = cx;
                if (cx > maxX) maxX = cx;
            }
            xPos.put(nodeId, (minX + maxX) / 2.0);
        }
    }

    private List<NestedNode> buildFullCallChain(Scanner_NestedElements.NestedElementIssue issue) throws SQLException {
        List<NestedNode> chain = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {

            Set<Integer> recursiveFunctionIds = loadRecursiveFunctionIds(conn);

            String sqlFunc = """
            SELECT mf.id, mf.function_name, mm.object_full_name
            FROM metadata_functions mf
            JOIN metadata_modules mm ON mf.module_id = mm.id
            WHERE mf.id = ?
            """;

            String sqlElem = """
            SELECT ce.id, ce.element_name, ce.element_type, ce.start_line, ce.element_text,
                   mf.function_name, mm.object_full_name
            FROM code_elements ce
            JOIN metadata_functions mf ON ce.function_id = mf.id
            JOIN metadata_modules mm ON mf.module_id = mm.id
            WHERE ce.id = ?
            """;

            for (int id : issue.chainElementIds) {

                if (id < 0) {
                    int funcId = -id;
                    try (PreparedStatement ps = conn.prepareStatement(sqlFunc)) {
                        ps.setInt(1, funcId);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) continue;

                        boolean isRecursive = recursiveFunctionIds.contains(funcId);
                        String funcName = rs.getString("function_name");
                        String label = isRecursive
                                ? "⚠️ " + funcName + " [РЕКУРСИЯ]"
                                : "📄 " + funcName;

                        chain.add(new NestedNode(
                                label,
                                isRecursive ? NodeType.RECURSIVE : NodeType.FUNCTION,
                                rs.getString("object_full_name"),
                                id, 0, funcName, 0, ""
                        ));
                    }
                    continue;
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlElem)) {
                    ps.setInt(1, id);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) continue;

                    String type = rs.getString("element_type");
                    String name = rs.getString("element_name");

                    NodeType nodeType = switch (type) {
                        case "ЦиклНезависимый", "ЦиклЗапроса" -> NodeType.CYCLE;
                        case "Транзакция" -> NodeType.TRANSACTION;
                        case "Блокировка" -> NodeType.LOCK;
                        default -> null;
                    };
                    if (nodeType == null) continue;

                    String icon = switch (nodeType) {
                        case CYCLE -> "🔄 ";
                        case TRANSACTION -> "💎 ";
                        case LOCK -> "🔒 ";
                        default -> "";
                    };

                    chain.add(new NestedNode(
                            icon + name, nodeType,
                            rs.getString("object_full_name"),
                            id, 0,
                            rs.getString("function_name"),
                            rs.getInt("start_line"),
                            rs.getString("element_text")
                    ));
                }
            }
        }

        return chain;
    }

    private Shape createNestedNode(NestedNode node, double x, double y) {
        Shape shape = buildShape(node.nodeType, x, y);

        Color fill0, fill1, border;
        switch (node.nodeType) {
            case FUNCTION -> { fill0 = Color.web("#64B5F6"); fill1 = Color.web("#1976D2"); border = Color.web("#0D47A1"); }
            case CYCLE -> { fill0 = Color.web("#BA68C8"); fill1 = Color.web("#7B1FA2"); border = Color.web("#6A1B9A"); }
            case TRANSACTION -> { fill0 = Color.web("#81C784"); fill1 = Color.web("#388E3C"); border = Color.web("#2E7D32"); }
            case LOCK -> { fill0 = Color.web("#FFB74D"); fill1 = Color.web("#FF9800"); border = Color.web("#F57C00"); }
            case RECURSIVE -> { fill0 = Color.web("#EF9A9A"); fill1 = Color.web("#C62828"); border = Color.web("#B71C1C"); }
            default -> { fill0 = Color.web("#64B5F6"); fill1 = Color.web("#1976D2"); border = Color.web("#0D47A1"); }
        }

        shape.setFill(new LinearGradient(x, y - 20, x, y + 20, false, CycleMethod.NO_CYCLE,
                new Stop(0, fill0), new Stop(1, fill1)));
        shape.setStroke(border);
        shape.setStrokeWidth(2);

/*        if (node.nodeType == NodeType.RECURSIVE) {
            shape.setStrokeWidth(3);
            shape.getStrokeDashArray().addAll(8.0, 4.0);
        }*/

        // Красная окантовка для рекурсивных
        if (node.nodeType == NodeType.RECURSIVE) {
            addRecursiveOutline(x, y);
        }

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.gray(0.4));
        shadow.setRadius(8);
        shadow.setOffsetX(2);
        shadow.setOffsetY(2);
        shape.setEffect(shadow);

        Label label = new Label(node.displayName);
        label.setLayoutX(x + 35);
        label.setLayoutY(y - 10);
        label.setFont(Font.font("Arial", FontWeight.NORMAL, 11));
        label.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 5; " +
                "-fx-padding: 3 8; -fx-border-color: #CCCCCC; -fx-border-width: 1; -fx-border-radius: 5;");

        Tooltip tooltip = new Tooltip(buildTooltipText(node));
        Tooltip.install(shape, tooltip);
        Tooltip.install(label, tooltip);

        shape.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onNodeClickCallback != null)
                onNodeClickCallback.accept(node);
        });
        label.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && onNodeClickCallback != null)
                onNodeClickCallback.accept(node);
        });

        setupNodeHoverAnimation(shape);

        getGraphPane().getChildren().addAll(shape, label);
        return shape;
    }

    private void addRecursiveOutline(double x, double y) {
        Rectangle outline = new Rectangle(x - 34, y - 24, 68, 48);
        outline.setArcWidth(22);
        outline.setArcHeight(22);
        outline.setFill(null);
        outline.setStroke(Color.web("#F44336"));
        outline.setStrokeWidth(2.5);
        outline.getStrokeDashArray().addAll(6.0, 3.0);

        // Красное свечение
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#F44336"));
        glow.setRadius(10);
        glow.setSpread(0.3);
        outline.setEffect(glow);

        getGraphPane().getChildren().add(outline);
    }

    private Set<Integer> loadRecursiveFunctionIds(Connection conn) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT function_id FROM recursive_functions");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getInt(1));
        }
        return ids;
    }

    private Shape buildShape(NodeType type, double x, double y) {
        return switch (type) {
            case FUNCTION, RECURSIVE -> {
                Rectangle r = new Rectangle(x - 28, y - 18, 56, 36);
                r.setArcWidth(18);
                r.setArcHeight(18);
                yield r;
            }
            case CYCLE -> {
                Polygon diamond = new Polygon(
                        x, y - 26,
                        x + 26, y,
                        x, y + 26,
                        x - 26, y
                );
                yield diamond;
            }
            case TRANSACTION -> new Rectangle(x - 28, y - 18, 56, 36);
            case LOCK -> {
                double r = 26;
                Polygon hex = new Polygon();
                for (int i = 0; i < 6; i++) {
                    double angle = Math.toRadians(60 * i - 30);
                    hex.getPoints().addAll(x + r * Math.cos(angle), y + r * Math.sin(angle));
                }
                yield hex;
            }
        };
    }

    private String buildTooltipText(NestedNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.nodeType == NodeType.TRANSACTION) sb.append("💎 ТРАНЗАКЦИЯ\n");
        else if (node.nodeType == NodeType.LOCK) sb.append("🔒 БЛОКИРОВКА\n");
        else if (node.nodeType == NodeType.CYCLE) sb.append("🔄 ЦИКЛ\n");
        else sb.append("📦 ФУНКЦИЯ\n");

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📌 Имя: ").append(node.displayName).append("\n");
        sb.append("📁 Модуль: ").append(node.moduleName).append("\n");
        if (node.startLine > 0) sb.append("📍 Строка: ").append(node.startLine).append("\n");
        if (node.elementText != null && !node.elementText.trim().isEmpty()) {
            String truncated = node.elementText.length() > 150 ?
                    node.elementText.substring(0, 147) + "..." : node.elementText;
            sb.append("\n📝 Код:\n").append(truncated);
        }
        return sb.toString();
    }

    private void createBezierConnection(Shape startNode, Shape endNode, boolean isCycleConnection) {
        double startX = startNode.getBoundsInParent().getCenterX();
        double startY = startNode.getBoundsInParent().getCenterY();
        double endX = endNode.getBoundsInParent().getCenterX();
        double endY = endNode.getBoundsInParent().getCenterY();

        Path path = new Path();
        MoveTo moveTo = new MoveTo(startX, startY);

        double controlX1 = startX + (endX - startX) * 0.5;
        double controlY1 = startY + (endY - startY) * 0.3;
        double controlX2 = startX + (endX - startX) * 0.5;
        double controlY2 = startY + (endY - startY) * 0.7;

        CubicCurveTo curveTo = new CubicCurveTo(controlX1, controlY1, controlX2, controlY2, endX, endY);
        path.getElements().addAll(moveTo, curveTo);

        path.setStroke(DIRECT_CALL_COLOR);
        path.setStrokeWidth(2);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setFill(null);

        addArrowHead(path, endX, endY, DIRECT_CALL_COLOR);
        getGraphPane().getChildren().add(0, path);
    }

    private void addArrowHead(Path path, double x, double y, Color color) {
        double arrowSize = 10;
        PathElement lastElement = path.getElements().get(path.getElements().size() - 1);
        double prevX = x, prevY = y;

        if (lastElement instanceof CubicCurveTo curve) {
            prevX = curve.getControlX2();
            prevY = curve.getControlY2();
        }

        double angle = Math.atan2(y - prevY, x - prevX);
        Path arrow = new Path();
        MoveTo moveToArrow = new MoveTo(x, y);
        LineTo line1 = new LineTo(x - arrowSize * Math.cos(angle - Math.PI/6),
                y - arrowSize * Math.sin(angle - Math.PI/6));
        LineTo line2 = new LineTo(x - arrowSize * Math.cos(angle + Math.PI/6),
                y - arrowSize * Math.sin(angle + Math.PI/6));
        LineTo line3 = new LineTo(x, y);
        arrow.getElements().addAll(moveToArrow, line1, line2, line3);
        arrow.setStroke(color);
        arrow.setFill(color);
        arrow.setStrokeWidth(1);
        getGraphPane().getChildren().add(arrow);
    }

    private void setupNodeHoverAnimation(Shape shape) {
        shape.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), shape);
            st.setToX(1.15);
            st.setToY(1.15);
            st.play();
            DropShadow hoverShadow = new DropShadow();
            hoverShadow.setColor(Color.gray(0.3));
            hoverShadow.setRadius(12);
            hoverShadow.setOffsetX(3);
            hoverShadow.setOffsetY(3);
            shape.setEffect(hoverShadow);
            shape.setCursor(javafx.scene.Cursor.HAND);
        });

        shape.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), shape);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
            DropShadow normalShadow = new DropShadow();
            normalShadow.setColor(Color.gray(0.4));
            normalShadow.setRadius(8);
            normalShadow.setOffsetX(2);
            normalShadow.setOffsetY(2);
            shape.setEffect(normalShadow);
            shape.setCursor(javafx.scene.Cursor.DEFAULT);
        });
    }

    private void showNoDataMessage() {
        Label label = new Label("Выберите проблему для отображения графа");
        label.setLayoutX(300);
        label.setLayoutY(350);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #757575;");
        getGraphPane().getChildren().add(label);
    }

    private void showErrorMessage(String message) {
        Label label = new Label("Ошибка: " + message);
        label.setLayoutX(300);
        label.setLayoutY(350);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #D32F2F;");
        getGraphPane().getChildren().add(label);
    }

    @Override
    protected void applyGraphStyle() {}

    public enum NodeType {
        FUNCTION, CYCLE, TRANSACTION, LOCK, RECURSIVE
    }

    public static class NestedNode {
        public String displayName;
        public NodeType nodeType;
        public String moduleName;
        public int elementId;
        public int counter;
        public String functionName;
        public int startLine;
        public String elementText;

        public NestedNode(String displayName, NodeType nodeType, String moduleName,
                          int elementId, int counter, String functionName,
                          int startLine, String elementText) {
            this.displayName = displayName;
            this.nodeType = nodeType;
            this.moduleName = moduleName;
            this.elementId = elementId;
            this.counter = counter;
            this.functionName = functionName;
            this.startLine = startLine;
            this.elementText = elementText;
        }
    }

    private java.util.function.Consumer<NestedNode> onNodeClickCallback;

    public void setOnNodeClickCallback(java.util.function.Consumer<NestedNode> callback) {
        this.onNodeClickCallback = callback;
    }
}