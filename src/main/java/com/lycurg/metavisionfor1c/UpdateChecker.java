package com.lycurg.metavisionfor1c;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class UpdateChecker {

    private static final String API_URL = "https://lycurg.com/PROJ/projMetaVisionFor1C/latest_version.php";

    // В начало класса добавить:
    private static final String BLACKLIST_URL = "https://lycurg.com/PROJ/projMetaVisionFor1C/check_blacklist.php";


    private static final String CURRENT_VERSION = Application_MetaVision.VERSION;
    private static final long CHECK_DELAY_SECONDS = 5;
    private static final long CHECK_PERIOD_HOURS = 1;

    private static ScheduledExecutorService scheduler;
    private static long lastShownVersion = 0; // время последнего показа для этой версии


    public static void startPeriodicCheck() {
        System.out.println("🚀 UpdateChecker: шедулер запущен");
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Первая проверка через 10 секунд
        scheduler.schedule(() -> checkForUpdates(), 10, TimeUnit.SECONDS);

        // Затем каждый час
       // scheduler.scheduleAtFixedRate(() -> checkForUpdates(), 1, 1, TimeUnit.HOURS);
    }




//для теста
/*
    public static void startPeriodicCheck() {
        System.out.println("🚀 UpdateChecker: шедулер запущен");
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // ТЕСТОВЫЙ РЕЖИМ: проверка каждые 10 секунд
        scheduler.scheduleAtFixedRate(() -> {
            checkForUpdates();
        }, 2, 10, TimeUnit.SECONDS);
    }
*/

    public static void stopPeriodicCheck() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    private static void checkForUpdates() {
        System.out.println("🔍 UpdateChecker: проверка началась");
        System.out.println("   Текущая версия: " + CURRENT_VERSION);

        if (!isInternetAvailable()) {
            System.out.println("❌ Нет интернета");
            showNoInternetDialog();
            return;
        }

        System.out.println("✅ Интернет есть");

        // 🔥 ПРОВЕРКА ЧЁРНОГО СПИСКА
        if (checkBlacklist()) {
            System.out.println("🚫 Устройство в чёрном списке");
            return; // Программа закроется в диалоге
        }

        VersionInfo serverVersion = fetchLatestVersion();
        if (serverVersion == null) {
            System.out.println("❌ Не удалось получить версию с сервера");
            return;
        }

        System.out.println("   Версия на сервере: " + serverVersion.version);

        if (isNewerVersion(serverVersion.version, CURRENT_VERSION)) {
            System.out.println("🔥 Доступна новая версия!");
            showUpdateDialog(serverVersion);
        } else {
            System.out.println("✅ Версия актуальна");
        }
    }


    // Новый метод проверки чёрного списка
    private static boolean checkBlacklist() {
        try {
            String machineId = MachineIdGenerator.getMachineId();
            URL url = new URL(BLACKLIST_URL + "?machine_id=" + URLEncoder.encode(machineId, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String json = response.toString();
            if (json.contains("\"blocked\":true")) {
                String programName = "MetaVision for 1C";
                String comment = "Ваше устройство заблокировано";

                int nameStart = json.indexOf("\"program_name\":\"");
                if (nameStart > 0) {
                    int nameEnd = json.indexOf("\"", nameStart + 16);
                    programName = json.substring(nameStart + 16, nameEnd);
                }

                int commentStart = json.indexOf("\"comment\":\"");
                if (commentStart > 0) {
                    int commentEnd = json.indexOf("\"", commentStart + 11);
                    comment = json.substring(commentStart + 11, commentEnd);
                }

                showBlacklistDialog(programName, comment);
                return true;
            }
        } catch (Exception e) {
            System.err.println("❌ Ошибка проверки чёрного списка: " + e.getMessage());
        }
        return false;
    }

    // Диалог блокировки
    private static void showBlacklistDialog(String programName, String comment) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Доступ запрещён");
            alert.setHeaderText("Ваше устройство заблокировано в программе: " + programName);
            alert.setContentText(comment + "\n\nПриложение будет закрыто через 5 секунд.");
            alert.showAndWait();

            // Таймер на закрытие
            new Thread(() -> {
                try {
                    Thread.sleep(5000);
                    Platform.exit();
                    System.exit(0);
                } catch (InterruptedException e) {}
            }).start();
        });
    }



    private static boolean isInternetAvailable() {
        try {
            URL url = new URL("https://ya.ru/");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();

        //    writeLog("isInternetAvailable OK, code=" + responseCode);
            return responseCode >= 200 && responseCode < 500;

        } catch (Exception e) {
         ///   writeLog("isInternetAvailable FAIL: " + e.getClass().getName() + ": " + e.getMessage());
            return false;
        }
    }

/*
    private static void writeLog(String message) {
        try {
            String path = System.getProperty("user.home") + "/metavision_debug.txt";
            String line = new java.util.Date() + " | " + message + "\n";
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(path),
                    line,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {}
    }
*/

    public static void sendStatistic(String key, String value) {
        System.out.println("🔥 sendStatistic: " + key + " = " + value);
        new Thread(() -> {
            try {
                String machineId = MachineIdGenerator.getMachineId();
                String version = Application_MetaVision.VERSION;

                String urlString = String.format(
                        "https://lycurg.com/PROJ/projMetaVisionFor1C/statistic.php?machine_id=%s&version=%s&key=%s&value=%s",
                        URLEncoder.encode(machineId, "UTF-8"),
                        URLEncoder.encode(version, "UTF-8"),
                        URLEncoder.encode(key, "UTF-8"),
                        URLEncoder.encode(value, "UTF-8")
                );

                System.out.println("📤 URL: " + urlString);

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                System.out.println("📤 Ответ: " + responseCode);

            } catch (Exception e) {
                System.err.println("❌ Ошибка: " + e.getMessage());
            }
        }).start();
    }

    private static VersionInfo fetchLatestVersion() {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String json = response.toString();
            VersionInfo info = new VersionInfo();

            // program_name
            int nameStart = json.indexOf("\"program_name\":\"") + 16;
            int nameEnd = json.indexOf("\"", nameStart);
            if (nameStart > 15) {
                info.programName = json.substring(nameStart, nameEnd);
            }

            // version
            int verStart = json.indexOf("\"version\":\"") + 11;
            int verEnd = json.indexOf("\"", verStart);
            info.version = json.substring(verStart, verEnd);

            // changelog
            int logStart = json.indexOf("\"changelog\":\"") + 13;
            int logEnd = json.indexOf("\"", logStart);
            info.changelog = json.substring(logStart, logEnd)
                    .replace("\\n", "\n")
                    .replace("\\/", "/")
                    .replaceAll("[\r]", "")  // только \r убираем, \n оставляем
                    .trim();
            // download_url
            int dlStart = json.indexOf("\"download_url\":\"") + 16;
            int dlEnd = json.indexOf("\"", dlStart);
            info.downloadUrl = json.substring(dlStart, dlEnd)
                    .replace("\\/", "/")
                    .replaceAll("[\r\n]", "")
                    .trim();

// Очистка purchaseUrls
            int purStart = json.indexOf("\"purchase_urls\":[");
            if (purStart > 0) {
                int arrStart = json.indexOf("[", purStart);
                int arrEnd = json.indexOf("]", arrStart);
                String arrStr = json.substring(arrStart + 1, arrEnd);
                String[] urls = arrStr.split(",");
                for (String u : urls) {
                    String clean = u.trim()
                            .replace("\"", "")
                            .replace("\\/", "/")
                            .replace("\\r", "")   // ← добавить
                            .replace("\\n", "")   // ← добавить
                            .replaceAll("[\r\n]", "")
                            .trim();
                    if (!clean.isEmpty()) {
                        info.purchaseUrls.add(clean);
                    }
                }
            }

            return info;
        } catch (Exception e) {
            System.err.println("❌ Ошибка проверки версии: " + e.getMessage());
            return null;
        }
    }

    private static boolean isNewerVersion(String serverVer, String currentVer) {
        try {
            String[] serverParts = serverVer.split("\\.");
            String[] currentParts = currentVer.split("\\.");
            int maxLength = Math.max(serverParts.length, currentParts.length);

            for (int i = 0; i < maxLength; i++) {
                int serverNum = i < serverParts.length ? Integer.parseInt(serverParts[i]) : 0;
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (serverNum != currentNum) {
                    return serverNum > currentNum;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    private static void showNoInternetDialog() {
        Platform.runLater(() -> {
            try {
                Stage dialog = new Stage();
                dialog.setTitle("Нет подключения к интернету");
                dialog.initModality(Modality.APPLICATION_MODAL);
                dialog.setResizable(false);
                dialog.setWidth(450);
                dialog.setHeight(320);

                // Иконка окна
                try {
                    Image icon = new Image(UpdateChecker.class.getResourceAsStream("/icons/log_meta.png"));
                    dialog.getIcons().add(icon);
                } catch (Exception e) {
                    System.out.println("⚠️ Иконка не найдена");
                }

                VBox mainContainer = new VBox(0);
                mainContainer.setStyle("-fx-background-color: white;");

                // Шапка (оранжевая для предупреждения)
                VBox headerBox = new VBox(10);
                headerBox.setStyle("-fx-background-color: linear-gradient(to right, #e65100, #ff9800); -fx-padding: 20 30 15 30;");

                HBox headerContent = new HBox(15);
                headerContent.setAlignment(Pos.CENTER_LEFT);

                Label iconLabel = new Label("⚠️");
                iconLabel.setStyle("-fx-font-size: 36px;");

                VBox titleBox = new VBox(5);
                Label titleLabel = new Label("Нет подключения к интернету");
                titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

                Label subtitleLabel = new Label("Требуется подключение к сети");
                subtitleLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.8);");

                titleBox.getChildren().addAll(titleLabel, subtitleLabel);
                headerContent.getChildren().addAll(iconLabel, titleBox);
                headerBox.getChildren().add(headerContent);

                // Контент
                VBox contentBox = new VBox(20);
                contentBox.setPadding(new Insets(30, 30, 30, 30));
                contentBox.setStyle("-fx-background-color: #f8f9fa;");
                contentBox.setAlignment(Pos.CENTER);

                // Текст сообщения
                Text messageText = new Text("Для проверки обновлений и использования ИИ-функций\nтребуется подключение к интернету.\n\nПожалуйста, подключитесь к сети\nи перезапустите приложение.");
                messageText.setWrappingWidth(350);
                messageText.setStyle("-fx-font-size: 13px; -fx-fill: #555;");
                messageText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

                // Кнопка закрытия
                Button closeButton = new Button("Закрыть");
                closeButton.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 35; -fx-background-radius: 4; -fx-cursor: hand;");
                closeButton.setOnAction(e -> dialog.close());

                contentBox.getChildren().addAll(messageText, closeButton);
                mainContainer.getChildren().addAll(headerBox, contentBox);

                Scene scene = new Scene(mainContainer);
                dialog.setScene(scene);
                dialog.show();
            } catch (Exception e) {
                System.err.println("Ошибка показа диалога: " + e.getMessage());
            }
        });
    }

    private static void showUpdateDialog(VersionInfo info) {
        Platform.runLater(() -> {
            try {
                sendStatistic("new_version_available", info.version);

                Stage dialog = new Stage();
                dialog.setTitle("MetaVision PRO уже доступен!");
                dialog.initModality(Modality.APPLICATION_MODAL);
                dialog.setResizable(false);
                dialog.setWidth(520);
                dialog.setHeight(580);

                try {
                    Image icon = new Image(UpdateChecker.class.getResourceAsStream("/icons/log_meta.png"));
                    dialog.getIcons().add(icon);
                } catch (Exception e) {}

                VBox mainContainer = new VBox(0);
                mainContainer.setStyle("-fx-background-color: white;");

                // Шапка
                VBox headerBox = new VBox(10);
                headerBox.setStyle("-fx-background-color: linear-gradient(to right, #2c3e50, #3498db); -fx-padding: 20 30 15 30;");

                HBox headerContent = new HBox(15);
                headerContent.setAlignment(Pos.CENTER_LEFT);

                ImageView iconView = null;
                try {
                    Image icon = new Image(UpdateChecker.class.getResourceAsStream("/icons/log_meta.png"));
                    iconView = new ImageView(icon);
                    iconView.setFitWidth(40);
                    iconView.setFitHeight(40);
                    iconView.setPreserveRatio(true);
                } catch (Exception e) {}

                if (iconView != null) {
                    headerContent.getChildren().add(iconView);
                }

                VBox titleBox = new VBox(5);
                Label titleLabel = new Label("MetaVision for 1C PRO уже доступен!");
                titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white; -fx-wrap-text: true;");

                // ИСПРАВЛЕНО: MetaVision for 1C PRO
                Label versionLabel = new Label("MetaVision for 1C PRO");
                versionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(255,255,255,0.8);");

                titleBox.getChildren().addAll(titleLabel, versionLabel);
                headerContent.getChildren().add(titleBox);
                headerBox.getChildren().add(headerContent);

                // Контент
                VBox contentBox = new VBox(12);
                contentBox.setPadding(new Insets(20, 25, 20, 25));
                contentBox.setStyle("-fx-background-color: #f8f9fa;");

                // ВАШ ТЕКСТ НАД "Что нового" (НЕ в скролле)
                Text promoText = new Text(
                        "Спасибо, что пользуетесь MetaVision! Ваш интерес помогает проекту развиваться.\n\n" +
                                "Для профессионалов, занимающихся аудитом и безопасностью, появилась отдельный проект: 'MetaVision for 1C PRO'. " +
                                "Он включает инструменты, которые не вошли в бесплатную версию."
                );
                promoText.setWrappingWidth(440);
                promoText.setStyle("-fx-font-size: 12px; -fx-fill: #333; -fx-font-weight: normal;");
                promoText.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);

                Label changelogTitle = new Label("📋 Что нового:");
                changelogTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #333;");

                // ТОЛЬКО CHANGELOG В СКРОЛЛЕ
                Text changelogText = new Text(info.changelog);
                changelogText.setWrappingWidth(440);
                changelogText.setStyle("-fx-font-size: 12px; -fx-fill: #555;");

                ScrollPane changelogScroll = new ScrollPane(changelogText);
                changelogScroll.setFitToWidth(true);
                changelogScroll.setPrefHeight(200);
                changelogScroll.setMaxHeight(250);
                changelogScroll.setStyle("-fx-background: #fafafa; -fx-background-color: #fafafa; -fx-border-color: #ddd; -fx-border-radius: 4;");

                // Блок ссылок
                HBox purchaseBox = new HBox(12);
                purchaseBox.setAlignment(Pos.CENTER_LEFT);
                purchaseBox.setStyle("-fx-padding: 5 0 5 0;");

                Label purchaseTitle = new Label("🛒 Где приобрести:");
                purchaseTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #333;");
                purchaseBox.getChildren().add(purchaseTitle);

                for (String url : info.purchaseUrls) {
                    String displayUrl = url.replace("https://", "").replace("http://", "");
                    if (displayUrl.length() > 30) {
                        displayUrl = displayUrl.substring(0, 27) + "...";
                    }
                    Hyperlink link = new Hyperlink(displayUrl);
                    link.setStyle("-fx-font-size: 11px; -fx-text-fill: #3498db;");
                    link.setCursor(javafx.scene.Cursor.HAND);
                    link.setOnAction(e -> openUrl(url));
                    link.setTooltip(new Tooltip(url));
                    purchaseBox.getChildren().add(link);
                }

                Button closeButton = new Button("Закрыть");
                closeButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 35; -fx-background-radius: 4; -fx-cursor: hand;");
                closeButton.setOnAction(e -> dialog.close());

                HBox buttonBox = new HBox();
                buttonBox.setAlignment(Pos.CENTER);
                buttonBox.setPadding(new Insets(12, 0, 0, 0));
                buttonBox.getChildren().add(closeButton);

                contentBox.getChildren().addAll(
                        promoText,
                        changelogTitle,
                        changelogScroll,
                        purchaseBox,
                        buttonBox
                );

                mainContainer.getChildren().addAll(headerBox, contentBox);

                Scene scene = new Scene(mainContainer);
                dialog.setScene(scene);
                dialog.show();

            } catch (Exception e) {
                System.err.println("Ошибка показа диалога: " + e.getMessage());
            }
        });
    }

    private static void openUrl(String url) {
        new Thread(() -> {
            try {
                // Очищаем от экранированных последовательностей
                String cleanUrl = url
                        .replace("\\/", "/")
                        .replace("\\r", "")   // ← убираем буквальные \r
                        .replace("\\n", "")   // ← убираем буквальные \n
                        .replace("\r", "")
                        .replace("\n", "")
                        .trim();

                if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                    cleanUrl = "https://" + cleanUrl;
                }

                java.awt.Desktop.getDesktop().browse(new java.net.URI(cleanUrl));
            } catch (Exception e) {
                System.err.println("Ошибка открытия ссылки: " + e.getMessage());
                System.err.println("Проблемный URL: " + url);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Ошибка");
                    alert.setHeaderText("Не удалось открыть ссылку");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private static class VersionInfo {
        String programName = "MetaVision for 1C";
        String version = "";
        String changelog = "";
        List<String> purchaseUrls = new ArrayList<>();
        String downloadUrl = "";
    }
}