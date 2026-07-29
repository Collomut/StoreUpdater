package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.User;
import com.stockmanager.util.Session;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    public void initialize() {
        errorLabel.setText("");
        
        passwordField.setOnAction(e -> handleLogin());
        usernameField.setOnAction(e -> passwordField.requestFocus());
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        try {
            User user = DatabaseManager.getInstance().authenticate(username, password);
            if (user == null) {
                showError("Incorrect username or password.");
                passwordField.clear();
                return;
            }
            Session.setUser(user);
            openMainApp();
        } catch (Exception e) {
            showError("Could not connect to database. Check your internet connection.");
            e.printStackTrace();
        }
    }

    private void openMainApp() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) usernameField.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 750);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Stock Manager — " + Session.getUser().getUsername());
            stage.setResizable(true);   
            stage.setMinWidth(900);
            stage.setMinHeight(600);
        } catch (Exception e) {
            showError("Failed to load main window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
    }
}
