// BaseGraphView.java - УПРОЩЕННАЯ ВЕРСИЯ
package com.lycurg.metavisionfor1c;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.application.Platform;
import java.util.*;

/**
 * Базовый класс для всех графов в приложении MetaVision.
 * Содержит только ОБЩУЮ функциональность: зум, панорамирование, стили.
 */
public abstract class BaseGraphView extends ScrollPane {

    // ========== СТИЛИ ГРАФА ==========
    public enum GraphStyle { CLASSIC, MODERN, MINIMALIST }
    protected GraphStyle currentStyle = GraphStyle.CLASSIC;

    // ========== ВИЗУАЛЬНЫЕ КОМПОНЕНТЫ ==========
    protected final Group zoomGroup;
    protected final Pane graphPane;
    protected final Pane backgroundPane;
    protected double scale = 1.0;

    // ========== КОНСТРУКТОР ==========
    public BaseGraphView() {
        backgroundPane = new Pane();
        graphPane = new Pane();
        zoomGroup = new Group(graphPane);

        Pane mainContainer = new Pane();
        mainContainer.getChildren().addAll(backgroundPane, zoomGroup);

        setContent(mainContainer);
        setPannable(false);
        setFitToWidth(true);
        setFitToHeight(true);

        // 🔥 БЕЛЫЙ ФОН С ЛЕГКИМ ГРАДИЕНТОМ
        backgroundPane.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #ffffff 0%, #f8f9fa 100%); " +
                "-fx-border-color: #e0e0e0; -fx-border-width: 1; -fx-border-radius: 3;");

        backgroundPane.prefWidthProperty().bind(mainContainer.widthProperty());
        backgroundPane.prefHeightProperty().bind(mainContainer.heightProperty());

        graphPane.setMinWidth(2000);
        graphPane.setMinHeight(2000);
        graphPane.setMouseTransparent(false);
        graphPane.setPickOnBounds(false);

        setupZoomAndPan();
        Platform.runLater(() -> centerView());
    }

    // ========== ОБЩИЕ МЕТОДЫ ==========

    /**
     * Центрирует вид графа
     */
    protected void centerView() {
        zoomGroup.setTranslateX(-80);
        zoomGroup.setTranslateY(-80);
    }

    /**
     * Устанавливает стиль графа
     */
    public void setGraphStyle(GraphStyle style) {
        this.currentStyle = style;
        applyGraphStyle();
    }

    /**
     * Получает текущий стиль графа
     */
    public GraphStyle getGraphStyle() {
        return currentStyle;
    }

    /**
     * Применяет текущий стиль ко всем элементам графа
     */
    protected abstract void applyGraphStyle();

    /**
     * Очищает граф
     */
    public void clear() {
        graphPane.getChildren().clear();
    }

    /**
     * Сбрасывает масштаб и позицию графа
     */
    public void resetZoom() {
        scale = 1.0;
        zoomGroup.setScaleX(1.0);
        zoomGroup.setScaleY(1.0);
        zoomGroup.setTranslateX(0);
        zoomGroup.setTranslateY(0);
    }



    /**
     * Создает снимок графа в виде изображения
     * @return WritableImage снимок графа
     */
    public javafx.scene.image.WritableImage takeSnapshot() {
        try {
            // Сохраняем текущий стиль фона
            String originalStyle = backgroundPane.getStyle();

            // Временно делаем фон белым для снимка
            backgroundPane.setStyle("-fx-background-color: white;");

            // Принудительно обновляем layout
            backgroundPane.applyCss();
            backgroundPane.layout();

            // Делаем снимок
            javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(javafx.scene.paint.Color.WHITE);
            javafx.scene.image.WritableImage snapshot = this.snapshot(params, null);

            // Восстанавливаем стиль
            backgroundPane.setStyle(originalStyle);

            return snapshot;
        } catch (Exception e) {
            System.err.println("❌ Ошибка создания снимка: " + e.getMessage());
            return null;
        }
    }
    /**
     * Обрезает текст до максимальной длины
     */
    protected String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    // ========== ЗУМ И ПАНОРАМИРОВАНИЕ ==========

    /**
     * Настраивает зум и панорамирование (ТОЧНО КАК В ОРИГИНАЛЕ)
     */
    private void setupZoomAndPan() {
        final Point2D[] dragAnchor = new Point2D[1];
        final Point2D[] translateAnchor = new Point2D[1];
        Pane mainContainer = (Pane) zoomGroup.getParent();

        // Панорамирование фона
        backgroundPane.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown() || e.isSecondaryButtonDown()) {
                dragAnchor[0] = new Point2D(e.getSceneX(), e.getSceneY());
                translateAnchor[0] = new Point2D(zoomGroup.getTranslateX(), zoomGroup.getTranslateY());
                e.consume();
            }
        });

        backgroundPane.setOnMouseDragged(e -> {
            if (dragAnchor[0] != null && translateAnchor[0] != null &&
                    (e.isPrimaryButtonDown() || e.isSecondaryButtonDown())) {
                double dx = e.getSceneX() - dragAnchor[0].getX();
                double dy = e.getSceneY() - dragAnchor[0].getY();
                zoomGroup.setTranslateX(translateAnchor[0].getX() + dx);
                zoomGroup.setTranslateY(translateAnchor[0].getY() + dy);
                e.consume();
            }
        });

        backgroundPane.setOnMouseReleased(e -> {
            dragAnchor[0] = null;
            translateAnchor[0] = null;
        });

        // Панорамирование области графа
        graphPane.setOnMousePressed(e -> {
            if (e.getTarget() == graphPane && (e.isPrimaryButtonDown() || e.isSecondaryButtonDown())) {
                dragAnchor[0] = new Point2D(e.getSceneX(), e.getSceneY());
                translateAnchor[0] = new Point2D(zoomGroup.getTranslateX(), zoomGroup.getTranslateY());
                e.consume();
            }
        });

        graphPane.setOnMouseDragged(e -> {
            if (dragAnchor[0] != null && translateAnchor[0] != null &&
                    (e.isPrimaryButtonDown() || e.isSecondaryButtonDown())) {
                double dx = e.getSceneX() - dragAnchor[0].getX();
                double dy = e.getSceneY() - dragAnchor[0].getY();
                zoomGroup.setTranslateX(translateAnchor[0].getX() + dx);
                zoomGroup.setTranslateY(translateAnchor[0].getY() + dy);
                e.consume();
            }
        });

        graphPane.setOnMouseReleased(e -> {
            if (e.getTarget() == graphPane) {
                dragAnchor[0] = null;
                translateAnchor[0] = null;
            }
        });

        // Зум колесиком мыши
        mainContainer.setOnScroll(e -> {
            e.consume();
            double deltaY = e.getDeltaY();
            if (deltaY == 0) return;

            double zoomFactor = (deltaY > 0) ? 1.1 : 0.9;
            double oldScale = scale;
            double newScale = oldScale * zoomFactor;
            newScale = Math.max(0.1, Math.min(newScale, 5.0));
            if (Math.abs(newScale - oldScale) < 0.01) return;

            double sceneX = e.getSceneX();
            double sceneY = e.getSceneY();
            Point2D zoomGroupPoint = zoomGroup.sceneToLocal(sceneX, sceneY);
            double localX = zoomGroupPoint.getX();
            double localY = zoomGroupPoint.getY();

            double oldTranslateX = zoomGroup.getTranslateX();
            double oldTranslateY = zoomGroup.getTranslateY();

            zoomGroup.setScaleX(newScale);
            zoomGroup.setScaleY(newScale);
            scale = newScale;

            Point2D newZoomGroupPoint = zoomGroup.localToScene(localX, localY);
            double newTranslateX = oldTranslateX + (sceneX - newZoomGroupPoint.getX());
            double newTranslateY = oldTranslateY + (sceneY - newZoomGroupPoint.getY());

            zoomGroup.setTranslateX(newTranslateX);
            zoomGroup.setTranslateY(newTranslateY);
        });

        // Двойной клик для сброса
        mainContainer.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.isPrimaryButtonDown()) {
                resetZoom();
                centerView();
                e.consume();
            }
        });
    }

    // ========== ГЕТТЕРЫ ==========

    public Pane getGraphPane() {
        return graphPane;
    }

    public Group getZoomGroup() {
        return zoomGroup;
    }

    public double getScale() {
        return scale;
    }
}