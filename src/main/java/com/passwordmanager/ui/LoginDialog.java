package com.passwordmanager.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.Node;
import com.passwordmanager.security.SecurityKeyManager;
import java.io.File;

public class LoginDialog extends Dialog<LoginResult> {
    private PasswordField passwordField;
    private TextField visiblePasswordField;
    private TextField keyFilePathField;
    private Button browseButton;

    public LoginDialog(Window owner) {
        setTitle("Login");
        setHeaderText("Enter your master password and select your key file");
        initOwner(owner);

        // Create the form fields
        passwordField = new PasswordField();
        visiblePasswordField = new TextField();
        keyFilePathField = new TextField();
        browseButton = new Button("Browse");

        // Create password toggle
        CheckBox showPasswordCheckBox = new CheckBox("Show Password");
        
        // Set up password visibility toggle
        visiblePasswordField.setManaged(false);
        visiblePasswordField.setVisible(false);
        showPasswordCheckBox.setOnAction(e -> {
            passwordField.setManaged(!showPasswordCheckBox.isSelected());
            passwordField.setVisible(!showPasswordCheckBox.isSelected());
            visiblePasswordField.setManaged(showPasswordCheckBox.isSelected());
            visiblePasswordField.setVisible(showPasswordCheckBox.isSelected());
        });

        // Bind password fields bidirectionally
        passwordField.textProperty().bindBidirectional(visiblePasswordField.textProperty());

        // Set up the file chooser
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Key File");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Key Files", "*.key")
            );
            File file = fileChooser.showOpenDialog(owner);
            if (file != null) {
                keyFilePathField.setText(file.getAbsolutePath());
            }
        });

        // Create the layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Master Password:"), 0, 0);
        HBox passwordBox = new HBox(10);
        passwordBox.getChildren().addAll(passwordField, visiblePasswordField);
        grid.add(passwordBox, 1, 0);
        grid.add(showPasswordCheckBox, 2, 0);
        
        grid.add(new Label("Key File:"), 0, 1);
        grid.add(keyFilePathField, 1, 1);
        grid.add(browseButton, 2, 1);

        getDialogPane().setContent(grid);

        // Add buttons
        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Enable/Disable login button based on form validation
        Node loginButton = getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        // Add validation listeners
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(loginButton));
        visiblePasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(loginButton));
        keyFilePathField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(loginButton));

        // Set the result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new LoginResult(
                    passwordField.getText(),
                    keyFilePathField.getText()
                );
            }
            return null;
        });
    }

    private void validateForm(Node loginButton) {
        boolean isValid = !passwordField.getText().isEmpty() &&
                         !keyFilePathField.getText().isEmpty();
        loginButton.setDisable(!isValid);
    }
} 