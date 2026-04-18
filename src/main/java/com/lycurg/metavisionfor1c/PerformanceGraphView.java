package com.lycurg.metavisionfor1c;

import javafx.scene.control.Label;
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

/**
 * Граф производительности для отображения цепочек вызовов с проблемами
 * Наследуется от BaseGraphView для использования общего функционала зума/панорамирования
 */
public class PerformanceGraphView extends BaseGraphView {

    // ========== ЦВЕТА ДЛЯ РАЗНЫХ ТИПОВ УЗЛОВ ==========

    private static final Color PROBLEM_BORDER = Color.web("#D32F2F");
    private static final Color FUNCTION_BORDER = Color.web("#1976D2");
    private static final Color CYCLE_BORDER = Color.web("#7B1FA2");

    private static final Color DIRECT_CALL_COLOR = Color.web("#4CAF50");      // Зеленый для вызовов
    private static final Color CYCLE_CALL_COLOR = Color.web("#FF9800");       // Оранжевый для вызовов в цикле


    private static final Color RECURSIVE_BORDER = Color.web("#9C27B0"); // Фиолетовый
    private static final Color RECURSIVE_FILL = Color.web("#E1BEE7");

    // ========== КОНСТАНТЫ РАЗМЕЩЕНИЯ ==========
    private static final double NODE_RADIUS = 30;
    private static final double CENTER_X = 500;
    private static final double CENTER_Y = 400;
    private static final double VERTICAL_SPACING = 120;
    private static final double HORIZONTAL_SPACING = 150;

    private Scanner_Performance.PerformanceIssue currentIssue;
    private final Map<Integer, Shape> nodeMap = new HashMap<>(); // functionId -> узел

    // ========== КОНСТРУКТОР ==========
    public PerformanceGraphView() {
        super();
        setGraphStyle(GraphStyle.CLASSIC);
        applyGraphStyle();
    }

    // ========== ОСНОВНОЙ МЕТОД ПОСТРОЕНИЯ ГРАФА ==========
    public void buildPerformanceGraph(Scanner_Performance.PerformanceIssue issue) {
        clear();
        nodeMap.clear();
        currentIssue = issue;

        if (issue == null) {
            showNoDataMessage();
            return;
        }

        try {
            List<PerformanceNode> chain = buildFullCallChain(issue);
            if (chain.isEmpty()) {
                showNoDataMessage();
                return;
            }

            layoutAndDrawChain(chain);

            // 🔥 НАЧАЛЬНЫЙ МАСШТАБ И ПОЗИЦИЯ
            resetZoom();


        } catch (Exception e) {
            showErrorMessage("Ошибка построения графа: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void resetZoom() {
        // BaseGraphView сбрасывает zoomGroup — нам нужно ещё сбросить graphPane
        super.resetZoom();
        getGraphPane().setTranslateY(-650); // смещение вверх
        getGraphPane().setTranslateX(-500); // смещение вбок
        getGraphPane().setScaleX(0.4);
        getGraphPane().setScaleY(0.4);
    }



    // ========== ПОСТРОЕНИЕ ПОЛНОЙ ЦЕПОЧКИ ВЫЗОВОВ ==========
    // ========== ПОСТРОЕНИЕ ПОЛНОЙ ЦЕПОЧКИ ВЫЗОВОВ (ПОЛНОСТЬЮ ИСПРАВЛЕННЫЙ) ==========
    private List<PerformanceNode> buildFullCallChain(Scanner_Performance.PerformanceIssue issue)
            throws SQLException {

        List<PerformanceNode> chain = new ArrayList<>();

        // Берём готовую цепочку из Scanner — не пересчитываем
        List<Integer> callChainIds = issue.chainFunctionIds;
        if (callChainIds == null || callChainIds.isEmpty()) {
            callChainIds = new ArrayList<>();
            callChainIds.add(issue.functionId);
        }

        System.out.println("🔗 Цепочка ID (из Scanner): " + callChainIds);

        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl())) {

            int cycleCounter = 1;

            for (int i = 0; i < callChainIds.size(); i++) {
                int funcId = callChainIds.get(i);

                String funcSql = """
                SELECT mf.function_name, mm.object_full_name as module_name
                FROM metadata_functions mf
                JOIN metadata_modules mm ON mf.module_id = mm.id
                WHERE mf.id = ?
                """;

                String functionName = "";
                String moduleName = "";

                try (PreparedStatement ps = conn.prepareStatement(funcSql)) {
                    ps.setInt(1, funcId);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        functionName = rs.getString("function_name");
                        moduleName = rs.getString("module_name");
                    }
                }

                boolean isProblemFunction = (funcId == issue.functionId);
                int recursionType = getRecursionType(funcId);
                boolean isDirectRecursion = (recursionType == 1);
                boolean isIndirectRecursion = (recursionType == 2);

                PerformanceNode functionNode;

                if (isProblemFunction && isDirectRecursion) {
                    functionNode = new PerformanceNode(
                            functionName, NodeType.RECURSIVE_PROBLEM, moduleName,
                            funcId, 0, null
                    );
                    functionNode.displayComment = "⚠️⚠️ ПРЯМАЯ РЕКУРСИЯ + ЗАПРОС В ЦИКЛЕ ⚠️⚠️";
                } else if (isProblemFunction && isIndirectRecursion) {
                    functionNode = new PerformanceNode(
                            functionName, NodeType.INDIRECT_RECURSIVE, moduleName,
                            funcId, 0, null
                    );
                    functionNode.displayComment = "⚠️⚠️ КОСВЕННАЯ РЕКУРСИЯ + ЗАПРОС В ЦИКЛЕ ⚠️⚠️";
                } else if (isProblemFunction) {
                    functionNode = new PerformanceNode(
                            functionName, NodeType.PROBLEM, moduleName,
                            funcId, 0, null
                    );
                    functionNode.displayComment = "⚠️ ЗАПРОС В ФУНКЦИИ";
                } else if (isDirectRecursion) {
                    functionNode = new PerformanceNode(
                            functionName, NodeType.RECURSIVE_PROBLEM, moduleName,
                            funcId, 0, null
                    );
                    functionNode.displayComment = "⚠️ ПРЯМАЯ РЕКУРСИЯ";
                } else if (isIndirectRecursion) {
                    functionNode = new PerformanceNode(
                            functionName, NodeType.INDIRECT_RECURSIVE, moduleName,
                            funcId, 0, null
                    );
                    functionNode.displayComment = "⚠️ КОСВЕННАЯ РЕКУРСИЯ";
                } else {
                    functionNode = new PerformanceNode(
                            functionName, NodeType.FUNCTION, moduleName,
                            funcId, 0, null
                    );
                }

                chain.add(functionNode);

                // Циклы только для не-проблемных у которых есть следующая функция
                if (!isProblemFunction && i + 1 < callChainIds.size()) {
                    int nextFuncId = callChainIds.get(i + 1);
                    List<Integer> wrappingCycleIds = getCyclesWrappingCall(funcId, nextFuncId, conn);

                    System.out.println("🔍 Функция " + functionName +
                            " → вызывает ID=" + nextFuncId +
                            " | циклов-обёрток: " + wrappingCycleIds.size());

                    for (int cycleId : wrappingCycleIds) {
                        PerformanceNode cycleNode = new PerformanceNode(
                                "", NodeType.CYCLE, moduleName, cycleId, cycleCounter++, functionNode
                        );
                        cycleNode.displayComment = "🔄 ЦИКЛ";
                        chain.add(cycleNode);
                    }
                }
            }

            System.out.println("✅ Итого узлов: " + chain.size() +
                    " (функций: " + chain.stream().filter(n -> n.nodeType != NodeType.CYCLE).count() +
                    ", циклов: " + chain.stream().filter(n -> n.nodeType == NodeType.CYCLE).count() + ")");

        } catch (SQLException e) {
            System.err.println("❌ Ошибка: " + e.getMessage());
            e.printStackTrace();
        }

        return chain;
    }

    private List<Integer> getCyclesWrappingCall(int functionId, int calledFunctionId, Connection conn)
            throws SQLException {

        // Находим все call-элементы из functionId в calledFunctionId
        String callsSql = """
        SELECT id FROM code_elements
        WHERE function_id = ?
          AND called_function_id = ?
          AND element_type = 'ВызовФункции'
        """;

        List<Integer> callElementIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(callsSql)) {
            ps.setInt(1, functionId);
            ps.setInt(2, calledFunctionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) callElementIds.add(rs.getInt("id"));
        }

        // Для каждого call-элемента считаем его циклы-предки
        // Берём тот у которого максимум (как Scanner)
        List<Integer> bestCycleIds = new ArrayList<>();
        int maxCycles = 0;

        String ancestorSql = """
        WITH RECURSIVE ancestor_chain AS (
            SELECT id, owner_id, element_type
            FROM code_elements
            WHERE id = ?
            
            UNION
            
            SELECT ce.id, ce.owner_id, ce.element_type
            FROM code_elements ce
            JOIN ancestor_chain ac ON ce.id = ac.owner_id
            WHERE ac.owner_id IS NOT NULL
        )
        SELECT DISTINCT id FROM ancestor_chain
        WHERE element_type IN ('ЦиклНезависимый', 'ЦиклЗапроса')
        ORDER BY id
        """;

        for (int callElementId : callElementIds) {
            List<Integer> cycleIds = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(ancestorSql)) {
                ps.setInt(1, callElementId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) cycleIds.add(rs.getInt("id"));
            }
            if (cycleIds.size() > maxCycles) {
                maxCycles = cycleIds.size();
                bestCycleIds = cycleIds;
            }
        }

        System.out.println("   🔍 уникальных циклов-обёрток: " + bestCycleIds.size() +
                " ids=" + bestCycleIds);
        return bestCycleIds;
    }
    private int getMaxDepth(CycleHierarchy cycle) {
        if (cycle.children.isEmpty()) return 0;
        int max = 0;
        for (CycleHierarchy child : cycle.children) {
            max = Math.max(max, 1 + getMaxDepth(child));
        }
        return max;
    }

    private void flattenCycleChildren(CycleHierarchy cycle, List<CycleHierarchy> result) {
        for (CycleHierarchy child : cycle.children) {
            result.add(child);
            flattenCycleChildren(child, result);
        }
    }


    // Класс для иерархии циклов
    private static class CycleHierarchy {
        int id;
        String type;
        String name;
        int startLine;
        int endLine;
        int ownerId;
        List<CycleHierarchy> children = new ArrayList<>();
        int depth; // глубина вложенности
    }

    // Создаёт узел для цикла
    private PerformanceNode createCycleNode(CycleHierarchy cycle, String moduleName,
                                            int counter, PerformanceNode parentFunction) {
        PerformanceNode cycleNode = new PerformanceNode(
                "",
                NodeType.CYCLE,
                moduleName,
                cycle.id,  // ← сохраняем ID цикла
                counter,
                parentFunction
        );

        // Формируем подпись с глубиной
        if (cycle.depth > 0) {
            cycleNode.displayComment = "🔄 ЦИКЛ (вложенность: " + cycle.depth + ")";
        } else {
            cycleNode.displayComment = "🔄 ЦИКЛ";
        }

        cycleNode.startLine = cycle.startLine;
        cycleNode.cycleCount = 1;

        return cycleNode;
    }

    // Рекурсивно добавляет дочерние циклы

    // Исправленный метод:
    private void addChildCycles(PerformanceNode parentCycleNode, List<CycleHierarchy> children,
                                List<PerformanceNode> chain, int[] counter, PerformanceNode parentFunction) {
        for (CycleHierarchy childCycle : children) {
            PerformanceNode childNode = createCycleNode(childCycle, parentCycleNode.moduleName,
                    counter[0]++, parentFunction);
            chain.add(childNode);

            if (!childCycle.children.isEmpty()) {
                addChildCycles(childNode, childCycle.children, chain, counter, parentFunction);
            }
        }
    }

    // ========== РАЗМЕЩЕНИЕ И ОТРИСОВКА ЦЕПОЧКИ ==========
// ========== РАЗМЕЩЕНИЕ И ОТРИСОВКА ЦЕПОЧКИ (ИСПРАВЛЕННЫЙ) ==========
    private void layoutAndDrawChain(List<PerformanceNode> chain) {
        if (chain.isEmpty()) return;

        double currentY = CENTER_Y;
        double currentXOffset = 0;
        int currentDepth = 0;

        for (int i = 0; i < chain.size(); i++) {
            PerformanceNode node = chain.get(i);

            // Определяем глубину вложенности для цикла
            if (node.nodeType == NodeType.CYCLE) {
                // Если предыдущий узел тоже цикл и он родительский
                if (i > 0 && chain.get(i-1).nodeType == NodeType.CYCLE) {
                    currentDepth++;
                    currentXOffset = currentDepth * 80; // смещение вправо
                } else {
                    currentDepth = 0;
                    currentXOffset = 0;
                }
            } else {
                currentDepth = 0;
                currentXOffset = 0;
            }

            double x = CENTER_X + currentXOffset;
            if (node.nodeType == NodeType.CYCLE) {
                x = CENTER_X + HORIZONTAL_SPACING + currentXOffset;
            }

            Shape nodeShape = createPerformanceNode(node, x, currentY);
            nodeMap.put(node.functionId, nodeShape);

            // Связи
            if (i > 0) {
                PerformanceNode previousNode = chain.get(i - 1);
                Shape previousShape = nodeMap.get(previousNode.functionId);
                if (previousShape != null) {
                    createBezierConnection(previousShape, nodeShape, true);
                }
            }

            currentY += VERTICAL_SPACING;
        }
    }

    // 🔥 НОВЫЙ МЕТОД: изогнутые стрелки (как раньше)
    private void createBezierConnection(Shape startNode, Shape endNode, boolean isCycleConnection) {
        double startX = startNode.getBoundsInParent().getCenterX();
        double startY = startNode.getBoundsInParent().getCenterY();
        double endX = endNode.getBoundsInParent().getCenterX();
        double endY = endNode.getBoundsInParent().getCenterY();

        Path path = new Path();
        MoveTo moveTo = new MoveTo(startX, startY);

        // 🔥 ИЗОГНУТАЯ ЛИНИЯ (как раньше)
        double controlX1, controlY1, controlX2, controlY2;

        if (isCycleConnection) {
            // Для связей с циклами — сильный изгиб
            controlX1 = startX + (endX - startX) * 0.3;
            controlY1 = startY - 40;
            controlX2 = startX + (endX - startX) * 0.7;
            controlY2 = endY + 40;
        } else {
            // Для обычных связей — плавный изгиб
            controlX1 = startX + (endX - startX) * 0.5;
            controlY1 = startY + (endY - startY) * 0.3;
            controlX2 = startX + (endX - startX) * 0.5;
            controlY2 = startY + (endY - startY) * 0.7;
        }

        CubicCurveTo curveTo = new CubicCurveTo(controlX1, controlY1, controlX2, controlY2, endX, endY);
        path.getElements().addAll(moveTo, curveTo);

        // Цвет и стиль
        Color lineColor = isCycleConnection ? Color.web("#FF9800") : Color.web("#4CAF50");
        path.setStroke(lineColor);
        path.setStrokeWidth(isCycleConnection ? 2.5 : 2.0);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setFill(null);

        if (isCycleConnection) {
            path.getStrokeDashArray().addAll(8.0, 5.0);  // пунктир для циклов
        }

        // Стрелка на конце
        addArrowHead(path, endX, endY, lineColor);

        getGraphPane().getChildren().add(0, path);
    }

    // 🔥 ВОССТАНОВИ СТАРЫЙ МЕТОД addArrowHead (рабочий)
    private void addArrowHead(Path path, double x, double y, Color color) {
        double arrowSize = 10;

        PathElement lastElement = path.getElements().get(path.getElements().size() - 1);
        double prevX = x, prevY = y;

        if (lastElement instanceof CubicCurveTo) {
            CubicCurveTo curve = (CubicCurveTo) lastElement;
            prevX = curve.getControlX2();
            prevY = curve.getControlY2();
        }

        double angle = Math.atan2(y - prevY, x - prevX);

        Path arrow = new Path();
        MoveTo moveToArrow = new MoveTo(x, y);
        LineTo line1 = new LineTo(
                x - arrowSize * Math.cos(angle - Math.PI/6),
                y - arrowSize * Math.sin(angle - Math.PI/6)
        );
        LineTo line2 = new LineTo(
                x - arrowSize * Math.cos(angle + Math.PI/6),
                y - arrowSize * Math.sin(angle + Math.PI/6)
        );
        LineTo line3 = new LineTo(x, y);

        arrow.getElements().addAll(moveToArrow, line1, line2, line3);
        arrow.setStroke(color);
        arrow.setFill(color);
        arrow.setStrokeWidth(1);

        getGraphPane().getChildren().add(arrow);
    }

    // ========== СОЗДАНИЕ КРАСИВОГО УЗЛА (как в DependencyGraphView) ==========
    // ========== СОЗДАНИЕ УЗЛА (ГАРАНТИРОВАННО СОЗДАЕТ КРАСНЫЙ КРУЖОК ДЛЯ PROBLEM) ==========
    private Shape createPerformanceNode(PerformanceNode node, double x, double y) {
        double nodeRadius = NODE_RADIUS;
        Circle circle = new Circle(x, y, nodeRadius);

        System.out.println("🎨 createPerformanceNode: " + node.name + " | nodeType=" + node.nodeType);

        RadialGradient gradient;
        Color borderColor;

        // ========== ОПРЕДЕЛЯЕМ ТИП УЗЛА И ЦВЕТ ==========
        switch (node.nodeType) {

            case RECURSIVE_PROBLEM:
                // Фиолетово-красный для рекурсивных проблем (самый опасный)
                gradient = new RadialGradient(
                        0, 0, x - nodeRadius/3, y - nodeRadius/3, nodeRadius * 1.5,
                        false, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#E1BEE7")),   // Светло-фиолетовый
                        new Stop(0.5, Color.web("#CE93D8")),  // Средне-фиолетовый
                        new Stop(1, Color.web("#AB47BC"))     // Темно-фиолетовый
                );
                borderColor = Color.web("#6A1B9A");
                circle.setStrokeWidth(3);
                circle.setStroke(Color.web("#9C27B0"));
                // Добавляем пульсирующий эффект для рекурсивных проблем
                addPulsingEffect(circle);
                break;

            case PROBLEM:
                // Ярко-красный для обычных проблем
                gradient = new RadialGradient(
                        0, 0, x - nodeRadius/3, y - nodeRadius/3, nodeRadius * 1.5,
                        false, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.rgb(255, 100, 100)),
                        new Stop(0.5, Color.rgb(220, 50, 50)),
                        new Stop(1, Color.rgb(180, 0, 0))
                );
                borderColor = Color.rgb(255, 0, 0);
                circle.setStrokeWidth(3);
                circle.setStroke(Color.rgb(255, 80, 80));
                System.out.println("🔴 СОЗДАН КРАСНЫЙ КРУЖОК ДЛЯ: " + node.name);
                break;


            case INDIRECT_RECURSIVE:
                gradient = new RadialGradient(
                        0, 0, x - nodeRadius/3, y - nodeRadius/3, nodeRadius * 1.5,
                        false, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#9E9E9E")),   // Светло-серый
                        new Stop(0.5, Color.web("#757575")),  // Средне-серый
                        new Stop(1, Color.web("#424242"))     // Тёмно-серый
                );
                borderColor = Color.web("#212121");
                circle.setStrokeWidth(3);
                circle.setStroke(Color.web("#616161"));
                break;


            case CYCLE:
                // Фиолетовый для циклов
                gradient = new RadialGradient(
                        0, 0, x - nodeRadius/3, y - nodeRadius/3, nodeRadius * 1.5,
                        false, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#BA68C8")),
                        new Stop(1, Color.web("#7B1FA2"))
                );
                borderColor = Color.web("#6A1B9A");
                circle.setStrokeWidth(2);
                break;

            case FUNCTION:
            default:
                // Синий для обычных функций
                gradient = new RadialGradient(
                        0, 0, x - nodeRadius/3, y - nodeRadius/3, nodeRadius * 1.5,
                        false, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#64B5F6")),
                        new Stop(1, Color.web("#1976D2"))
                );
                borderColor = Color.web("#0D47A1");
                circle.setStrokeWidth(2);
                break;
        }

        circle.setFill(gradient);
        circle.setStroke(borderColor);

        // Тень
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.gray(0.4));
        shadow.setRadius(8);
        shadow.setOffsetX(2);
        shadow.setOffsetY(2);
        circle.setEffect(shadow);

        // ========== НАЗВАНИЕ ПОД КРУЖКОМ ==========
        String displayName;
        if (node.nodeType == NodeType.RECURSIVE_PROBLEM) {
            displayName = "⚠️⚠️ [ПРЯМАЯ РЕКУРСИЯ] " + node.name;
            if (displayName.length() > 60) {
                displayName = displayName.substring(0, 57) + "...";
            }
        } else if (node.nodeType == NodeType.INDIRECT_RECURSIVE) {
            displayName = "⚠️⚠️ [КОСВЕННАЯ РЕКУРСИЯ] " + node.name;
            if (displayName.length() > 60) {
                displayName = displayName.substring(0, 57) + "...";
            }
        } else if (node.nodeType == NodeType.PROBLEM) {
            displayName = "⚠️ " + node.name;
            if (displayName.length() > 55) {
                displayName = displayName.substring(0, 52) + "...";
            }
        } else if (node.nodeType == NodeType.CYCLE) {
            displayName = node.displayComment != null && !node.displayComment.isEmpty()
                    ? node.displayComment : "🔄 Цикл";
        } else {
            displayName = node.name;
            if (displayName.length() > 60) {
                displayName = displayName.substring(0, 57) + "...";
            }
        }

        Label label = new Label(displayName);
        label.setLayoutX(x - nodeRadius - 10);
        label.setLayoutY(y + nodeRadius + 5);
        label.setFont(Font.font("Arial", FontWeight.NORMAL, 10));

        String labelStyle = "-fx-background-color: rgba(255, 255, 255, 0.95); " +
                "-fx-background-radius: 5px; " +
                "-fx-padding: 4px 8px; " +
                "-fx-border-color: #CCCCCC; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 5px;";

        if (node.nodeType == NodeType.PROBLEM) {
            labelStyle += "-fx-border-color: #FF0000; -fx-border-width: 2px; -fx-font-weight: bold;";
            label.setTextFill(Color.rgb(200, 0, 0));
        } else if (node.nodeType == NodeType.RECURSIVE_PROBLEM) {
            labelStyle += "-fx-border-color: #9C27B0; -fx-border-width: 3px; -fx-font-weight: bold;";
            label.setTextFill(Color.web("#6A1B9A"));
        }

        label.setStyle(labelStyle);

        getGraphPane().getChildren().add(label);

        // ========== ПОДСКАЗКА ==========
        Tooltip tooltip = new Tooltip(buildFullTooltipText(node));
        tooltip.setStyle("-fx-font-size: 12px; -fx-max-width: 400; -fx-wrap-text: true;");
        Tooltip.install(circle, tooltip);
        Tooltip.install(label, tooltip);

        setupNodeHoverAnimation(circle, node.nodeType);

        circle.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) onNodeClick(node);
        });

        getGraphPane().getChildren().add(circle);

        return circle;
    }
    // ПОЛУЧАЕТ ТЕКСТ ЦИКЛА ПО ЕГО ID
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
    // 🔥 ПОЛНАЯ ПОДСКАЗКА
    private String buildFullTooltipText(PerformanceNode node) {
        StringBuilder sb = new StringBuilder();

        if (node.nodeType == NodeType.INDIRECT_RECURSIVE) {
            sb.append("⚫⚫⚫ КОСВЕННАЯ РЕКУРСИЯ ⚫⚫⚫\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("⚠️ Функция участвует в косвенной рекурсии (A→B→C→A)\n");
            sb.append("Это сложный для отладки тип рекурсии.\n\n");
            sb.append("📌 Функция: ").append(node.name).append("\n");
            if (node.startLine > 0) {
                sb.append("📍 Строка: ").append(node.startLine).append("\n");
            }
            sb.append("📁 Модуль: ").append(node.moduleName).append("\n");
            if (currentIssue != null) {
                sb.append("\n🔴 Проблема: ").append(currentIssue.type).append("\n");
                sb.append("⚠️ Уровень: ").append(currentIssue.severity).append("\n");
                sb.append("🔄 Циклов в цепочке: ").append(currentIssue.chainDepth).append("\n");
            }
            sb.append("\n💡 РЕКОМЕНДАЦИЯ:\n");
            sb.append("   - Разорвать цикл вызовов\n");
            sb.append("   - Вынести общую логику в отдельную функцию\n");
            sb.append("   - Перейти на итеративный алгоритм");
        }
        else if (node.nodeType == NodeType.RECURSIVE_PROBLEM) {
            sb.append("🔴🔴🔴 ПРЯМАЯ РЕКУРСИЯ + ЗАПРОС В ЦИКЛЕ 🔴🔴🔴\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("⚠️⚠️ ФУНКЦИЯ ВЫЗЫВАЕТ САМА СЕБЯ ⚠️⚠️\n");
            sb.append("Внутри неё обнаружен запрос в цикле!\n\n");
            sb.append("📈 Каждый уровень рекурсии УМНОЖАЕТ число запросов.\n");
            sb.append("    Если глубина рекурсии = 10, а цикл = 100 итераций,\n");
            sb.append("    то будет выполнено 1000 запросов вместо одного!\n\n");
            sb.append("📌 Функция: ").append(node.name).append("\n");
            if (node.startLine > 0) {
                sb.append("📍 Строка: ").append(node.startLine).append("\n");
            }
            sb.append("📁 Модуль: ").append(node.moduleName).append("\n");
            if (currentIssue != null) {
                sb.append("\n🔴 Проблема: ").append(currentIssue.type).append("\n");
                sb.append("⚠️ Уровень: ").append(currentIssue.severity).append("\n");
                sb.append("🔄 Циклов в цепочке: ").append(currentIssue.chainDepth).append("\n");
            }
            sb.append("\n💡 РЕКОМЕНДАЦИЯ: Полное перепроектирование!\n");
            sb.append("   - Преобразуйте рекурсию в итеративный алгоритм\n");
            sb.append("   - Вынесите запрос за пределы цикла\n");
            sb.append("   - Используйте кэширование данных");
        }
        else if (node.nodeType == NodeType.PROBLEM) {
            sb.append("⚠️ ПРОБЛЕМНЫЙ ЗАПРОС\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📌 Функция: ").append(node.name).append("\n");
            if (node.startLine > 0) {
                sb.append("📍 Строка: ").append(node.startLine).append("\n");
            }
            sb.append("📁 Модуль: ").append(node.moduleName).append("\n");
            if (currentIssue != null) {
                sb.append("\n🔴 Проблема: ").append(currentIssue.type).append("\n");
                sb.append("⚠️ Уровень: ").append(currentIssue.severity).append("\n");
                sb.append("🔄 Циклов в цепочке: ").append(currentIssue.chainDepth).append("\n");
            }
        }
        else if (node.nodeType == NodeType.CYCLE) {
            sb.append("🔄 ЦИКЛ\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📊 Уровень: ").append(node.cycleCount).append("\n");
            if (node.parentFunction != null) {
                sb.append("📦 В функции: ").append(node.parentFunction.name).append("\n");
            }
            sb.append("📁 Модуль: ").append(node.moduleName).append("\n");
            if (node.startLine > 0) {
                sb.append("📍 Строка: ").append(node.startLine).append("\n");
            }
            if (node.functionId > 0) {
                String cycleText = getCycleText(node.functionId);
                if (cycleText != null && !cycleText.trim().isEmpty()) {
                    sb.append("\n📝 Код цикла:\n");
                    String truncated = cycleText.length() > 150 ?
                            cycleText.substring(0, 147) + "..." : cycleText;
                    sb.append(truncated);
                }
            }
        }
        else {
            sb.append("📦 ФУНКЦИЯ\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📌 Имя: ").append(node.name).append("\n");
            sb.append("📁 Модуль: ").append(node.moduleName).append("\n");
            if (node.startLine > 0) {
                sb.append("📍 Строка: ").append(node.startLine).append("\n");
            }
            int elementsCount = countElementsInFunction(node.functionId);
            if (elementsCount > 0) {
                sb.append("📊 Элементов: ").append(elementsCount).append("\n");
            }
        }

        return sb.toString();
    }

    private int getRecursionType(int functionId) {
        if (functionId <= 0) return 0; // 0 = не рекурсивная
        String sql = "SELECT recursion_type FROM recursive_functions WHERE function_id = ?";
        try (Connection conn = DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, functionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String type = rs.getString("recursion_type");
                if ("DIRECT".equals(type)) return 1;      // прямая рекурсия
                if ("INDIRECT".equals(type)) return 2;    // косвенная рекурсия
            }
        } catch (SQLException e) {
            // Игнорируем
        }
        return 0;
    }


    // ПОДСЧЕТ КОЛИЧЕСТВА ЭЛЕМЕНТОВ В ФУНКЦИИ
    private int countElementsInFunction(int functionId) {
        String sql = "SELECT COUNT(*) FROM code_elements WHERE function_id = ?";

        try (Connection conn =DriverManager.getConnection(DBPathHelper.getDbUrl());
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, functionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.err.println("Ошибка подсчета элементов: " + e.getMessage());
        }

        return 0;
    }

   //========= АНИМАЦИЯ ПРИ НАВЕДЕНИИ ==========
    private void setupNodeHoverAnimation(Circle circle, NodeType nodeType) {
        circle.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), circle);
            st.setToX(1.15);
            st.setToY(1.15);
            st.play();

            // Подсветка
            DropShadow hoverShadow = new DropShadow();
            hoverShadow.setColor(getNodeBorderColor(nodeType).deriveColor(0, 1.0, 1.0, 0.5));
            hoverShadow.setRadius(12);
            hoverShadow.setOffsetX(3);
            hoverShadow.setOffsetY(3);
            circle.setEffect(hoverShadow);

            circle.setCursor(javafx.scene.Cursor.HAND);
        });

        circle.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), circle);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();

            // Возвращаем обычную тень
            DropShadow normalShadow = new DropShadow();
            normalShadow.setColor(Color.gray(0.4));
            normalShadow.setRadius(8);
            normalShadow.setOffsetX(2);
            normalShadow.setOffsetY(2);
            circle.setEffect(normalShadow);

            circle.setCursor(javafx.scene.Cursor.DEFAULT);
        });
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    private Color getNodeBorderColor(NodeType nodeType) {
        switch (nodeType) {
            case PROBLEM: return PROBLEM_BORDER;
            case CYCLE: return CYCLE_BORDER;
            case FUNCTION: return FUNCTION_BORDER;
            default: return Color.BLACK;
        }
    }

/*    private void onNodeClick(PerformanceNode node) {
        if (node.nodeType == NodeType.PROBLEM && currentIssue != null) {
            // Можно добавить действие при клике на проблемную функцию
            System.out.println("Клик на проблемную функцию: " + node.name);
        }
    }*/

    private void showNoDataMessage() {
        Label label = new Label("Выберите проблему производительности для отображения графа");
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

    // ========== ПРИМЕНЕНИЕ СТИЛЯ (из BaseGraphView) ==========
    @Override
    protected void applyGraphStyle() {
        // Применяем стиль ко всем элементам графа
        // Можно добавить разную цветовую схему для разных стилей
        switch (currentStyle) {
            case CLASSIC:
                // Используем стандартные цвета (уже заданы)
                break;
            case MODERN:
                // Более яркие цвета
                break;
            case MINIMALIST:
                // Минималистичные цвета
                break;
        }
    }


    /**
     * Добавляет пульсирующую анимацию для узлов с рекурсивными проблемами
     */
    private void addPulsingEffect(Circle circle) {
        javafx.animation.ScaleTransition pulse = new javafx.animation.ScaleTransition(
                javafx.util.Duration.millis(1000), circle
        );
        pulse.setToX(1.05);
        pulse.setToY(1.05);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        pulse.play();
    }


    // ========== ВНУТРЕННИЕ КЛАССЫ ==========
    public enum NodeType {
        PROBLEM,              // Обычная проблема (красный)
        RECURSIVE_PROBLEM,    // Прямая рекурсия + проблема (фиолетовый)
        INDIRECT_RECURSIVE,   // Косвенная рекурсия (тёмно-серый)
        FUNCTION,             // Обычная функция (синий)
        CYCLE                 // Цикл (фиолетовый)
    }
    // В PerformanceGraphView.java
    public static class PerformanceNode {
        public String name;                 // ← делаем public


        public String displayComment;
        public NodeType nodeType;
        public String moduleName;
        public int functionId;              // ← делаем public
        public int cycleCount;
        public int startLine;
        public String functionText;

        public PerformanceNode parentFunction;

        public PerformanceNode(String name, NodeType nodeType, String moduleName,
                               int functionId, int cycleCount, PerformanceNode parentFunction) {
            this.name = name;
            this.displayComment = "";
            this.nodeType = nodeType;
            this.moduleName = moduleName;
            this.functionId = functionId;
            this.cycleCount = cycleCount;
            this.parentFunction = parentFunction;
            this.startLine = 0;
            this.functionText = null;
        }
    }




    private java.util.function.Consumer<PerformanceNode> onNodeClickCallback;

    // Сеттер для callback
    public void setOnNodeClickCallback(java.util.function.Consumer<PerformanceNode> callback) {
        this.onNodeClickCallback = callback;
    }

    // В методе onNodeClick, который уже существует, добавьте:
    private void onNodeClick(PerformanceNode node) {
        System.out.println("Клик на узел: " + node.name + " funcId=" + node.functionId);
        // 🔥 Вызываем callback для любых узлов, у которых есть functionId
        if (onNodeClickCallback != null && node.functionId > 0) {
            onNodeClickCallback.accept(node);
        }
    }

}