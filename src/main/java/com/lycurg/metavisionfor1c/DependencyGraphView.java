package com.lycurg.metavisionfor1c;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;





//# Визуализатор графов зависимостей с продвинутой графикой (узлы, связи, анимации)
public class DependencyGraphView extends ScrollPane {

    private final Group zoomGroup;
    private final Pane canvas;
    private double scale = 1.0;

    // Цветовая палитра для графа
    private static final Color[] NODE_COLORS = {
            Color.web("#4A90E2"), // Синий
            Color.web("#50E3C2"), // Бирюзовый
            Color.web("#B8E986"), // Салатовый
            Color.web("#F5A623"), // Оранжевый
            Color.web("#BD10E0"), // Фиолетовый
            Color.web("#7ED321"), // Зеленый
            Color.web("#9013FE"), // Пурпурный
            Color.web("#417505")  // Темно-зеленый
    };

    public interface NodeClickListener { void onNodeClick(String name); }
    private NodeClickListener clickListener;

    public DependencyGraphView() {
        canvas = new Pane();

        // 🔥 УСТАНАВЛИВАЕМ БЕЛЫЙ ФОН для canvas
        canvas.setStyle("-fx-background-color: white;");

        zoomGroup = new Group(canvas);
        setContent(zoomGroup);
        setPannable(false);
        setFitToWidth(true);
        setFitToHeight(true);

        // 🔥 Устанавливаем белый фон для всего ScrollPane
        setStyle("-fx-background-color: white; -fx-border-color: #E0E0E0; -fx-border-width: 1px;");

        setupZoomAndPan();
    }

    private void setupZoomAndPan() {
        final Point2D[] dragStart = new Point2D[1];

        zoomGroup.setOnMousePressed(e -> {
            if (e.isSecondaryButtonDown()) {
                dragStart[0] = new Point2D(e.getSceneX(), e.getSceneY());
                e.consume();
            }
        });

        zoomGroup.setOnMouseDragged(e -> {
            if (e.isSecondaryButtonDown() && dragStart[0] != null) {
                double dx = e.getSceneX() - dragStart[0].getX();
                double dy = e.getSceneY() - dragStart[0].getY();
                zoomGroup.setTranslateX(zoomGroup.getTranslateX() + dx);
                zoomGroup.setTranslateY(zoomGroup.getTranslateY() + dy);
                dragStart[0] = new Point2D(e.getSceneX(), e.getSceneY());
                e.consume();
            }
        });

        zoomGroup.setOnScroll(e -> {
            e.consume();
            double deltaY = e.getDeltaY();
            if (Math.abs(deltaY) < 1.0) return;

            double zoomFactor = deltaY > 0 ? 1.15 : 0.85;
            double oldScale = scale;
            scale = Math.max(0.1, Math.min(scale * zoomFactor, 12.0));

            double mouseX = e.getX(), mouseY = e.getY();
            double pivotX = (mouseX - zoomGroup.getTranslateX()) / oldScale;
            double pivotY = (mouseY - zoomGroup.getTranslateY()) / oldScale;

            zoomGroup.setScaleX(scale);
            zoomGroup.setScaleY(scale);
            zoomGroup.setTranslateX(mouseX - pivotX * scale);
            zoomGroup.setTranslateY(mouseY - pivotY * scale);
        });
    }

    // 🔥 УЛУЧШЕННЫЙ МЕТОД ДЛЯ СОЗДАНИЯ УЗЛОВ
    public Shape createNode(String name, double x, double y, double radius, NodeType type) {
        Circle circle = new Circle(x, y, radius);

        // Градиент для красивого заполнения
        RadialGradient gradient = new RadialGradient(
                0, 0,                      // focusAngle, focusDistance
                x - radius/3, y - radius/3, // centerX, centerY
                radius * 1.5,               // radius
                false,                      // proportional
                CycleMethod.NO_CYCLE,       // cycleMethod
                new Stop(0, getNodeColor(type).deriveColor(0, 1.0, 1.0, 1.0)), // светлый центр
                new Stop(1, getNodeColor(type).deriveColor(0, 1.0, 0.7, 1.0))  // темнее по краям
        );

        circle.setFill(gradient);
        circle.setStroke(getBorderColor(type));
        circle.setStrokeWidth(2);

        // 🔥 ТЕНЬ для объемности
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.gray(0.4));
        shadow.setRadius(8);
        shadow.setOffsetX(2);
        shadow.setOffsetY(2);
        shadow.setSpread(0.1);
        circle.setEffect(shadow);

        // Создаем метку узла
        Label label = new Label(name);
        label.setLayoutX(x - radius);
        label.setLayoutY(y + radius + 5);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        label.setTextFill(Color.web("#333333"));

        // Фон для метки
        label.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); " +
                "-fx-background-radius: 3px; " +
                "-fx-padding: 2px 5px; " +
                "-fx-border-color: #E0E0E0; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 3px;");

        // Группируем круг и метку
        Group nodeGroup = new Group(circle, label);

        // 🔥 АНИМАЦИЯ при наведении
        circle.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), circle);
            st.setToX(1.1);
            st.setToY(1.1);
            st.play();

            // Усиливаем тень при наведении
            DropShadow hoverShadow = new DropShadow();
            hoverShadow.setColor(Color.web("#2196F3", 0.5));
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

        // Клик по узлу
        circle.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && clickListener != null) {
                // Анимация клика
                ScaleTransition clickAnim = new ScaleTransition(Duration.millis(100), circle);
                clickAnim.setToX(0.95);
                clickAnim.setToY(0.95);
                clickAnim.setAutoReverse(true);
                clickAnim.setCycleCount(2);
                clickAnim.play();

                clickListener.onNodeClick(name);
            }
        });

        canvas.getChildren().add(nodeGroup);
        return circle;
    }

    // 🔥 УЛУЧШЕННЫЙ МЕТОД ДЛЯ СОЗДАНИЯ СВЯЗЕЙ
    public Path createConnection(Shape startNode, Shape endNode, NodeConnectionType type) {
        Path path = new Path();

        double startX = startNode.getBoundsInParent().getCenterX();
        double startY = startNode.getBoundsInParent().getCenterY();
        double endX = endNode.getBoundsInParent().getCenterX();
        double endY = endNode.getBoundsInParent().getCenterY();

        // Создаем изогнутую линию (Безье)
        MoveTo moveTo = new MoveTo(startX, startY);

        // Контрольные точки для изгиба
        double controlX1 = startX + (endX - startX) / 2;
        double controlY1 = startY;
        double controlX2 = startX + (endX - startX) / 2;
        double controlY2 = endY;

        CubicCurveTo curveTo = new CubicCurveTo(controlX1, controlY1, controlX2, controlY2, endX, endY);
        path.getElements().addAll(moveTo, curveTo);

        // Стиль линии в зависимости от типа связи
        path.setStroke(getConnectionColor(type));
        path.setStrokeWidth(1.5);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setFill(null);

        // Пунктир для определенных типов связей
        if (type == NodeConnectionType.DEPENDENCY) {
            path.getStrokeDashArray().addAll(5.0, 5.0);
        }

        // Добавляем стрелку
        addArrowHead(path, endX, endY, getConnectionColor(type));

        canvas.getChildren().add(0, path); // Добавляем вниз стека, чтобы линии были под узлами
        return path;
    }

    // 🔥 МЕТОД ДЛЯ ДОБАВЛЕНИЯ СТРЕЛКИ
    private void addArrowHead(Path path, double x, double y, Color color) {
        double arrowSize = 8;

        // Вычисляем угол линии
        PathElement lastElement = path.getElements().get(path.getElements().size() - 1);
        double prevX = 0, prevY = 0;

        if (lastElement instanceof CubicCurveTo) {
            CubicCurveTo curve = (CubicCurveTo) lastElement;
            prevX = curve.getControlX2();
            prevY = curve.getControlY2();
        } else if (lastElement instanceof LineTo) {
            LineTo line = (LineTo) lastElement;
            prevX = line.getX();
            prevY = line.getY();
        }

        double angle = Math.atan2(y - prevY, x - prevX);

        // Создаем стрелку
        Path arrow = new Path();
        MoveTo moveToArrow = new MoveTo(
                x - arrowSize * Math.cos(angle - Math.PI/6),
                y - arrowSize * Math.sin(angle - Math.PI/6)
        );
        LineTo line1 = new LineTo(x, y);
        LineTo line2 = new LineTo(
                x - arrowSize * Math.cos(angle + Math.PI/6),
                y - arrowSize * Math.sin(angle + Math.PI/6)
        );

        arrow.getElements().addAll(moveToArrow, line1, line2);
        arrow.setStroke(color);
        arrow.setFill(color);
        arrow.setStrokeWidth(1);

        canvas.getChildren().add(arrow);
    }

    // 🔥 ПОЛУЧЕНИЕ ЦВЕТОВ ДЛЯ УЗЛОВ И СВЯЗЕЙ
    private Color getNodeColor(NodeType type) {
        switch (type) {
            case CENTER:
                return NODE_COLORS[0]; // Синий для центрального узла
            case CALLER:
                return NODE_COLORS[2]; // Салатовый для вызывающих
            case CALLEE:
                return NODE_COLORS[4]; // Фиолетовый для вызываемых
            default:
                return NODE_COLORS[1];
        }
    }

    private Color getBorderColor(NodeType type) {
        return getNodeColor(type).darker().darker();
    }

    private Color getConnectionColor(NodeConnectionType type) {
        switch (type) {
            case DIRECT_CALL:
                return Color.web("#4CAF50"); // Зеленый для прямых вызовов
            case INDIRECT_CALL:
                return Color.web("#FF9800"); // Оранжевый для косвенных
            case DEPENDENCY:
                return Color.web("#9C27B0"); // Фиолетовый для зависимостей
            default:
                return Color.web("#2196F3"); // Синий по умолчанию
        }
    }

    // 🔥 ОЧИСТКА ГРАФА
    public void clear() {
        canvas.getChildren().clear();
        zoomGroup.setTranslateX(0);
        zoomGroup.setTranslateY(0);
        zoomGroup.setScaleX(1.0);
        zoomGroup.setScaleY(1.0);
        scale = 1.0;
    }

    // 🔥 СБРОС МАСШТАБА
    public void resetZoom() {
        zoomGroup.setTranslateX(0);
        zoomGroup.setTranslateY(0);
        zoomGroup.setScaleX(1.0);
        zoomGroup.setScaleY(1.0);
        scale = 1.0;
    }

    // Установка слушателя кликов
    public void setOnNodeClickListener(NodeClickListener listener) {
        this.clickListener = listener;
    }

    // 🔥 ДОБАВЛЯЕМ ОТСУТСТВУЮЩИЕ ENUM (если их нет в вашем коде)
    public enum NodeType {
        CENTER, CALLER, CALLEE
    }

    public enum NodeConnectionType {
        DIRECT_CALL, INDIRECT_CALL, DEPENDENCY
    }
}