package com.stockmanager.controller;

import com.stockmanager.db.DatabaseManager;
import com.stockmanager.model.User;
import com.stockmanager.util.Session;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
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
            if (user.isMustChangePassword()) {
                if (showForcePasswordChangeDialog(user)) {
                    Session.setUser(user);
                    openMainApp();
                } else {
                    showError("Password change is required to log in.");
                }
                passwordField.clear();
                return;
            }
            Session.setUser(user);
            openMainApp();
        } catch (RuntimeException e) {
            showError(e.getMessage());
            passwordField.clear();
        } catch (Exception e) {
            showError("Could not connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean showForcePasswordChangeDialog(User user) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Change Password Required");
        dialog.setHeaderText("For security reasons, you must change your password before logging in.");

        ButtonType changeButtonType = new ButtonType("Change Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm New Password");

        grid.add(new Label("New Password:"), 0, 0);
        grid.add(newPasswordField, 1, 0);
        grid.add(new Label("Confirm Password:"), 0, 1);
        grid.add(confirmPasswordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Platform.runLater(() -> newPasswordField.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == changeButtonType) {
                return newPasswordField.getText();
            }
            return null;
        });

        // Validation loop
        while (true) {
            java.util.Optional<String> result = dialog.showAndWait();
            if (!result.isPresent()) {
                return false; // Cancelled
            }

            String newPass = result.get().trim();
            String confirmPass = confirmPasswordField.getText().trim();

            if (newPass.isEmpty()) {
                showAlertDialog("Error", "Password cannot be empty.");
                continue;
            }
            if (newPass.length() < 6) {
                showAlertDialog("Error", "Password must be at least 6 characters long.");
                continue;
            }
            if (!newPass.equals(confirmPass)) {
                showAlertDialog("Error", "Passwords do not match.");
                continue;
            }

            // Save to DB
            boolean success = DatabaseManager.getInstance().changePassword(user.getId(), newPass);
            if (success) {
                showAlertDialog("Success", "Password changed successfully! You will now be logged in.");
                user.setMustChangePassword(false);
                return true;
            } else {
                showAlertDialog("Error", "Failed to update password. Please try again.");
            }
        }
    }

    private void showAlertDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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
