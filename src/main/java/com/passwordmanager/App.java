package com.passwordmanager;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.passwordmanager.database.DatabaseManager;
import com.passwordmanager.ui.LoginDialog;
import com.passwordmanager.ui.SignUpDialog;
import com.passwordmanager.security.SecurityKeyManager;
import java.sql.SQLException;
import com.passwordmanager.ui.MainWindow;
import java.io.File;
import java.io.FileInputStream;
import com.passwordmanager.security.LoginAttemptManager;

public class App extends Application {
    private DatabaseManager dbManager;
    private static final String APP_TITLE = "The Password Vault";

    public static void main(String[] args) {
        try {
            launch(args);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("JavaFX runtime components are missing")) {
                System.err.println("JavaFX runtime is missing. Please make sure JavaFX is properly installed.");
                System.exit(1);
            }
            throw e;
        }
    }

    @Override
    public void start(Stage stage) {
        try {
            dbManager = new DatabaseManager();
        } catch (Exception e) {
            showError("Database Error", "Failed to initialize database.");
            e.printStackTrace();
            return;
        }

        // Create welcome screen
        VBox welcomeBox = new VBox(15);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.setPadding(new Insets(20));

        // Add logo
        try {
            Image logo = new Image(getClass().getResourceAsStream("/pics/VaultLogo.jpg"));
            ImageView logoView = new ImageView(logo);
            logoView.setFitHeight(150);
            logoView.setFitWidth(150);
            logoView.setPreserveRatio(true);
            if (logo.isError()) {
                throw new Exception("Failed to load image: " + logo.getException().getMessage());
            }
            welcomeBox.getChildren().add(logoView);
        } catch (Exception e) {
            System.err.println("Failed to load logo: " + e.getMessage());
            e.printStackTrace();
        }

        Label titleLabel = new Label(APP_TITLE);
        titleLabel.setStyle("""
            -fx-font-size: 28px;
            -fx-font-weight: bold;
            -fx-font-family: 'Segoe UI';
            -fx-text-fill: #2C3E50;
            """);
        titleLabel.setPadding(new Insets(0, 0, 20, 0));

        Button loginButton = createStyledButton("Login", false);
        Button signUpButton = createStyledButton("Sign Up", false);

        VBox buttonContainer = new VBox(10);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.getChildren().addAll(loginButton, signUpButton);

        welcomeBox.getChildren().addAll(titleLabel, buttonContainer);

        loginButton.setOnAction(e -> {
            LoginDialog loginDialog = new LoginDialog(stage);
            loginDialog.showAndWait().ifPresent(result -> {
                String username = result.getMasterPassword(); 
                LoginAttemptManager attemptManager = LoginAttemptManager.getInstance();
                
                if (attemptManager.isLockedOut(username)) {
                    long remainingSeconds = attemptManager.getRemainingLockoutSeconds(username);
                    showError("Account Locked", 
                        String.format("Too many failed attempts. Please try again in %d seconds.", remainingSeconds));
                    return;
                }
                
                try {
                    if (dbManager.verifyMasterPassword(result.getMasterPassword()) &&
                        SecurityKeyManager.verifyKeyFile(result.getMasterPassword(), result.getKeyFilePath())) {
                        
                        attemptManager.resetAttempts(username);
                        
                        dbManager.initializeDatabase(result.getMasterPassword());
                        showMainWindow(stage);
                    } else {
                        attemptManager.recordFailedAttempt(username);
                        
                        int remainingAttempts = attemptManager.getRemainingAttempts(username);
                        if (remainingAttempts > 0) {
                            showError("Login Failed", 
                                String.format("Invalid password or key file. %d attempts remaining.", remainingAttempts));
                        } else {
                            long lockoutSeconds = attemptManager.getRemainingLockoutSeconds(username);
                            showError("Account Locked", 
                                String.format("Too many failed attempts. Please try again in %d seconds.", lockoutSeconds));
                        }
                    }
                } catch (Exception ex) {
                    showError("Login Error", "An error occurred during login.");
                    ex.printStackTrace();
                }
            });
        });

        signUpButton.setOnAction(e -> {
            SignUpDialog signUpDialog = new SignUpDialog(stage);
            signUpDialog.showAndWait().ifPresent(result -> {
                try {
                    File keyFile = new File(result.getKeyFilePath());
                    if (keyFile.exists()) {
                        showError("Sign Up Failed", 
                            "A key file already exists at the specified location.\n" +
                            "Please choose a different location.");
                        return;
                    }

                    if (dbManager.createUser(result.getMasterPassword())) {
                        try {
                            SecurityKeyManager.generateKeyFile(
                                result.getMasterPassword(), 
                                result.getKeyFilePath()
                            );
                            
                            showInfo("Account Created", 
                                "Account created successfully!\n" +
                                "Your key file has been saved to:\n" + 
                                result.getKeyFilePath() + "\n\n" +
                                "IMPORTANT: Keep this file safe - you will need it to log in!");
                        } catch (Exception ex) {
                            try {
                                dbManager.deleteUser();
                            } catch (SQLException deleteEx) {
                                deleteEx.printStackTrace();
                            }
                            throw ex;
                        }
                    } else {
                        showError("Sign Up Failed", 
                            "Failed to create account.\n" +
                            "Please delete the database file and try again.");
                    }
                } catch (Exception ex) {
                    showError("Sign Up Error", 
                        "An error occurred during account creation: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        });

        Scene scene = new Scene(welcomeBox, 400, 450);
        scene.getRoot().setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 10;
            """);
        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.show();
    }

    private Button createStyledButton(String text, boolean isDestructive) {
        Button button = new Button(text);
        button.setStyle("""
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-padding: 12 30;
            -fx-background-radius: 5;
            -fx-border-radius: 5;
            -fx-border-width: 1;
            -fx-border-color: %s;
            -fx-cursor: hand;
            -fx-min-width: 200px;
            """.formatted(
                isDestructive ? "white" : "#0A84FF",
                isDestructive ? "#FF3B30" : "white",
                isDestructive ? "#FF3B30" : "#0A84FF"
            ));

        button.setOnMouseEntered(e -> button.setStyle("""
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-padding: 12 30;
            -fx-background-radius: 5;
            -fx-border-radius: 5;
            -fx-border-width: 1;
            -fx-border-color: %s;
            -fx-cursor: hand;
            -fx-min-width: 200px;
            -fx-opacity: 0.9;
            """.formatted(
                isDestructive ? "#FFF5F5" : "#0070E0",
                isDestructive ? "#FF3B30" : "white",
                isDestructive ? "#FF3B30" : "#0070E0"
            )));

        button.setOnMouseExited(e -> button.setStyle("""
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-padding: 12 30;
            -fx-background-radius: 5;
            -fx-border-radius: 5;
            -fx-border-width: 1;
            -fx-border-color: %s;
            -fx-cursor: hand;
            -fx-min-width: 200px;
            """.formatted(
                isDestructive ? "white" : "#0A84FF",
                isDestructive ? "#FF3B30" : "white",
                isDestructive ? "#FF3B30" : "#0A84FF"
            )));

        return button;
    }

    private void showMainWindow(Stage stage) {
        try {
            MainWindow mainWindow = new MainWindow(dbManager, stage);
            stage.setTitle(APP_TITLE);
            mainWindow.show();
        } catch (Exception e) {
            showError("Error", "Failed to open main window.");
            e.printStackTrace();
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (dbManager != null) {
            dbManager.closeConnection();
        }
    }
} 