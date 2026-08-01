package com.stockmanager;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.db.HttpDatabaseClient;
import com.stockmanager.util.AutoUpdater;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        DatabaseManager.getInstance();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(loader.load(), 460, 480);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setTitle("Stock Manager — Login");
        try {
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon.png")));
        } catch (Exception ignored) {}
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.centerOnScreen();

        // F-2 / H-5: Call logout on close to revoke the JWT token server-side
        primaryStage.setOnCloseRequest(e -> {
            if (HttpDatabaseClient.hasToken()) {
                try {
                    HttpDatabaseClient.post("/api/auth/logout", "{}");
                } catch (Exception ignored) {}
            }
        });

        primaryStage.show();

        // Check GitHub for updates in the background — never blocks startup
        AutoUpdater.checkForUpdatesAsync(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

