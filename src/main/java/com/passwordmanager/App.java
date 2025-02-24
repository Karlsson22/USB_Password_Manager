package com.passwordmanager;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import com.passwordmanager.database.DatabaseManager;
import com.passwordmanager.ui.LoginDialog;
import com.passwordmanager.ui.SignUpDialog;
import com.passwordmanager.security.SecurityKeyManager;
import java.sql.SQLException;
import com.passwordmanager.ui.MainWindow;
import java.io.File;

public class App extends Application {
    private DatabaseManager dbManager;

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
        VBox welcomeBox = new VBox(10);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.setPadding(new Insets(20));

        Label titleLabel = new Label("Password Manager");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Button loginButton = new Button("Login");
        Button signUpButton = new Button("Sign Up");
        Button resetButton = new Button("Reset Database");

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(loginButton, signUpButton, resetButton);

        welcomeBox.getChildren().addAll(titleLabel, buttonBox);

        // Handle login button
        loginButton.setOnAction(e -> {
            LoginDialog loginDialog = new LoginDialog(stage);
            loginDialog.showAndWait().ifPresent(result -> {
                try {
                    if (dbManager.verifyMasterPassword(result.getMasterPassword()) &&
                        SecurityKeyManager.verifyKeyFile(result.getMasterPassword(), result.getKeyFilePath())) {
                        
                        dbManager.initializeDatabase(result.getMasterPassword());
                        showMainWindow(stage);
                    } else {
                        showError("Login Failed", "Invalid password or key file.");
                    }
                } catch (Exception ex) {
                    showError("Login Error", "An error occurred during login.");
                    ex.printStackTrace();
                }
            });
        });

        // Handle sign up button
        signUpButton.setOnAction(e -> {
            SignUpDialog signUpDialog = new SignUpDialog(stage);
            signUpDialog.showAndWait().ifPresent(result -> {
                try {
                    // First check if key file already exists
                    File keyFile = new File(result.getKeyFilePath());
                    if (keyFile.exists()) {
                        showError("Sign Up Failed", 
                            "A key file already exists at the specified location.\n" +
                            "Please choose a different location.");
                        return;
                    }

                    // Try to create the user in database
                    if (dbManager.createUser(result.getMasterPassword())) {
                        try {
                            // Generate the key file
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
                            // If key file generation fails, delete the user
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

        // Handle reset button
        resetButton.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Reset Database");
            confirm.setHeaderText("Are you sure?");
            confirm.setContentText(
                "This will delete ALL data including accounts and passwords.\n" +
                "This action cannot be undone!");

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        dbManager.wipeDatabase();
                        showInfo("Database Reset", 
                            "The database has been reset.\n" +
                            "You can now create a new account.");
                    } catch (SQLException ex) {
                        showError("Reset Failed", 
                            "Failed to reset database: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            });
        });

        Scene scene = new Scene(welcomeBox, 300, 200);
        stage.setTitle("Password Manager");
        stage.setScene(scene);
        stage.show();
    }

    private void showMainWindow(Stage stage) {
        try {
            MainWindow mainWindow = new MainWindow(dbManager, stage);
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

    public static void main(String[] args) {
        launch();
    }
} 