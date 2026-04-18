package com.lycurg.metavisionfor1c;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.io.IOException;

public class Application_MetaVision extends Application {

    public static final String VERSION = "1.0";

    @Override
    public void start(Stage stage) throws IOException {
        // === ПРОВЕРКА ПЕРВОГО ЗАПУСКА ===
        MySettings settings = new MySettings();
        boolean licenseAccepted = "true".equals(settings.get("license_accepted"));

        if (!licenseAccepted) {
            WelcomeDialog welcomeDialog = new WelcomeDialog(stage);
            boolean accepted = welcomeDialog.showAndWait();

            if (accepted) {
                settings.set("license_accepted", "true");
            } else {
                Platform.exit();
                return;
            }
        }

        // === ДАЛЬШЕ ЗАГРУЗКА ПРОГРАММЫ ===
        System.out.println("1. Загрузка FXML...");
        FXMLLoader fxmlLoader = new FXMLLoader(Application_MetaVision.class.getResource("main-view.fxml"));

        System.out.println("2. Получение размеров экрана...");
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1320, screen.getWidth() - 50);
        double height = Math.min(900, screen.getHeight() - 80);

        System.out.println("3. Создание сцены...");
        Scene scene = new Scene(fxmlLoader.load(), width, height);

        System.out.println("4. Настройка окна...");
        stage.setMaximized(false);
        stage.setTitle("MetaVision for 1C");

        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/log_meta.png")));
            System.out.println("5. Иконка загружена");
        } catch (Exception e) {
            System.out.println("⚠️ Иконка не найдена");
        }

        stage.setScene(scene);
        stage.show();
        System.out.println("6. Окно показано");

        // Запускаем проверку обновлений в отдельном потоке (не блокирует UI)
        new Thread(() -> {
            try {
                Thread.sleep(3000); // ждём 3 секунды после запуска
                UpdateChecker.sendStatistic("os", System.getProperty("os.name"));
                UpdateChecker.startPeriodicCheck();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        stage.setOnCloseRequest(e -> {
            System.out.println("7. Закрытие...");
            DependencyController controller = fxmlLoader.getController();
            if (controller != null) {
                controller.shutdown();
            }
        });
    }

    public static void main(String[] args) {
        launch();
    }
}