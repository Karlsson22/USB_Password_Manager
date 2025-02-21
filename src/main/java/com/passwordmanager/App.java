package com.passwordmanager;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.passwordmanager.database.DatabaseManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.passwordmanager.ui.MainWindow;

public class App extends Application {
    private DatabaseManager dbManager;
    private Button loginButton;

    @Override
    public void start(Stage stage) {
        // Initialize database manager (but don't initialize database yet)
        dbManager = new DatabaseManager();

        // Create login form
        VBox loginBox = new VBox(10);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setPadding(new Insets(20));

        Label titleLabel = new Label("Password Manager");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label passwordLabel = new Label("Master Password:");
        PasswordField masterPasswordField = new PasswordField();
        masterPasswordField.setMaxWidth(200);

        loginButton = new Button("Login");
        loginButton.setOnAction(e -> handleLogin(masterPasswordField.getText()));

        loginBox.getChildren().addAll(
            titleLabel,
            passwordLabel,
            masterPasswordField,
            loginButton
        );

        Scene scene = new Scene(loginBox, 300, 200);
        stage.setTitle("Password Manager");
        stage.setScene(scene);
        stage.show();
    }

    private void handleLogin(String masterPassword) {
        if (masterPassword.isEmpty()) {
            System.out.println("Password cannot be empty");
            return;
        }

        try {
            // Initialize database with master password
            dbManager.initializeDatabase(masterPassword);

            // Check if this is first run (no users exist)
            Statement stmt = dbManager.getConnection().createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            boolean isFirstRun = rs.getInt(1) == 0;

            boolean loginSuccessful = false;
            if (isFirstRun) {
                // Create first user
                loginSuccessful = dbManager.createUser(masterPassword);
                if (loginSuccessful) {
                    System.out.println("First user created successfully!");
                }
            } else {
                // Verify existing user
                loginSuccessful = dbManager.verifyMasterPassword(masterPassword);
                if (loginSuccessful) {
                    System.out.println("Login successful!");
                } else {
                    System.out.println("Invalid password!");
                }
            }

            // Show main window if login was successful
            if (loginSuccessful) {
                MainWindow mainWindow = new MainWindow(dbManager, (Stage) loginButton.getScene().getWindow());
                mainWindow.show();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        // Close database connection when application closes
        if (dbManager != null) {
            dbManager.closeConnection();
        }
    }

    public static void main(String[] args) {
        launch();
    }
} 