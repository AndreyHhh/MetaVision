package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class WelcomeDialog {

    private final Stage ownerStage;
    private boolean accepted = false;

    public WelcomeDialog(Stage ownerStage) {
        this.ownerStage = ownerStage;
    }

    public boolean showAndWait() {
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Добро пожаловать!");
        dialogStage.setResizable(false);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.initOwner(ownerStage);
        dialogStage.setWidth(500);
        dialogStage.setHeight(350);

        // Иконка окна
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icons/log_meta.png"));
            dialogStage.getIcons().add(icon);
        } catch (Exception e) {
            System.out.println("⚠️ Иконка не загружена");
        }

        VBox mainContainer = new VBox(0);
        mainContainer.setStyle("-fx-background-color: white;");

        // Шапка
        VBox headerBox = new VBox(10);
        headerBox.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #3498db); -fx-padding: 20 30 15 30;");

        HBox headerContent = new HBox(15);
        headerContent.setAlignment(Pos.CENTER_LEFT);

        // Иконка в шапке
        ImageView iconView = null;
        try {
            Image icon = new Image(getClass().getResourceAsStream("/icons/log_meta.png"));
            iconView = new ImageView(icon);
            iconView.setFitWidth(45);
            iconView.setFitHeight(45);
            iconView.setPreserveRatio(true);
        } catch (Exception e) {}

        if (iconView != null) {
            headerContent.getChildren().add(iconView);
        }

        VBox titleBox = new VBox(5);
        Label titleLabel = new Label("MetaVision for 1C");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitleLabel = new Label("Добро пожаловать!");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.8);");

        titleBox.getChildren().addAll(titleLabel, subtitleLabel);
        headerContent.getChildren().add(titleBox);
        headerBox.getChildren().add(headerContent);

        // Контент
        VBox contentBox = new VBox(25);
        contentBox.setPadding(new Insets(30, 30, 30, 30));
        contentBox.setStyle("-fx-background-color: #f8f9fa;");
        contentBox.setAlignment(Pos.CENTER);

        Label infoLabel = new Label("Используя данную программу, вы подтверждаете,\nчто ознакомлены и согласны с условиями");
        infoLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");
        infoLabel.setAlignment(Pos.CENTER);

        // Ссылка на лицензию
        Hyperlink licenseLink = new Hyperlink("Лицензионное соглашение");
        licenseLink.setStyle("-fx-font-size: 13px; -fx-text-fill: #3498db; -fx-border-color: transparent;");
        licenseLink.setCursor(javafx.scene.Cursor.HAND);
        licenseLink.setOnAction(e -> {
            new Thread(() -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://lycurg.com/privacy/MetaVision/%D0%9B%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D0%BE%D0%BD%D0%BD%D0%BE%D0%B5%20%D1%81%D0%BE%D0%B3%D0%BB%D0%B0%D1%88%D0%B5%D0%BD%D0%B8%D0%B5%20MetaVision%20for%201C.pdf"));
                } catch (Exception ex) {
                    System.err.println("Ошибка открытия ссылки");
                }
            }).start();
        });

        // Большая кнопка
        Button agreeButton = new Button("✓ Принять и продолжить");
        agreeButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12 25; -fx-background-radius: 4; -fx-cursor: hand;");
        agreeButton.setOnAction(e -> {
            accepted = true;
            dialogStage.close();
        });

        contentBox.getChildren().addAll(infoLabel, licenseLink, agreeButton);
        mainContainer.getChildren().addAll(headerBox, contentBox);

        dialogStage.setOnCloseRequest(e -> {
            if (!accepted) {
                Platform.exit();
                System.exit(0);
            }
        });

        Scene scene = new Scene(mainContainer);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();

        return accepted;
    }
}