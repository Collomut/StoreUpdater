package com.stockmanager;

import com.stockmanager.db.DatabaseManager;
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
        primaryStage.show();

        // Check GitHub for updates in the background — never blocks startup
        AutoUpdater.checkForUpdatesAsync(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
