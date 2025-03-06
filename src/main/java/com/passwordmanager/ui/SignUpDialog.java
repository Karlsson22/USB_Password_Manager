package com.passwordmanager.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.Node;
import java.io.File;

public class SignUpDialog extends Dialog<SignUpResult> {
    private PasswordField passwordField;
    private TextField visiblePasswordField;
    private PasswordField confirmPasswordField;
    private TextField visibleConfirmPasswordField;
    private TextField keyFilePathField;
    private Button browseButton;

    public SignUpDialog(Window owner) {
        setTitle("Create Account");
        setHeaderText("Set up your master password and choose where to save your security key file");
        initOwner(owner);

        passwordField = new PasswordField();
        visiblePasswordField = new TextField();
        confirmPasswordField = new PasswordField();
        visibleConfirmPasswordField = new TextField();
        keyFilePathField = new TextField();
        browseButton = new Button("Choose Location");

        CheckBox showPasswordsCheckBox = new CheckBox("Show Passwords");
        
        visiblePasswordField.setManaged(false);
        visiblePasswordField.setVisible(false);
        visibleConfirmPasswordField.setManaged(false);
        visibleConfirmPasswordField.setVisible(false);
        
        showPasswordsCheckBox.setOnAction(e -> {
            passwordField.setManaged(!showPasswordsCheckBox.isSelected());
            passwordField.setVisible(!showPasswordsCheckBox.isSelected());
            visiblePasswordField.setManaged(showPasswordsCheckBox.isSelected());
            visiblePasswordField.setVisible(showPasswordsCheckBox.isSelected());
            
            confirmPasswordField.setManaged(!showPasswordsCheckBox.isSelected());
            confirmPasswordField.setVisible(!showPasswordsCheckBox.isSelected());
            visibleConfirmPasswordField.setManaged(showPasswordsCheckBox.isSelected());
            visibleConfirmPasswordField.setVisible(showPasswordsCheckBox.isSelected());
        });

        passwordField.textProperty().bindBidirectional(visiblePasswordField.textProperty());
        confirmPasswordField.textProperty().bindBidirectional(visibleConfirmPasswordField.textProperty());

        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choose Location to Save Key File");
            fileChooser.setInitialFileName("password_manager.key");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Key Files", "*.key")
            );
            File file = fileChooser.showSaveDialog(owner);
            if (file != null) {
                keyFilePathField.setText(file.getAbsolutePath());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(showPasswordsCheckBox, 1, 0);

        grid.add(new Label("Master Password:"), 0, 1);
        HBox passwordBox = new HBox(10);
        passwordBox.getChildren().addAll(passwordField, visiblePasswordField);
        grid.add(passwordBox, 1, 1);

        grid.add(new Label("Confirm Password:"), 0, 2);
        HBox confirmPasswordBox = new HBox(10);
        confirmPasswordBox.getChildren().addAll(confirmPasswordField, visibleConfirmPasswordField);
        grid.add(confirmPasswordBox, 1, 2);

        grid.add(new Label("Key File Location:"), 0, 3);
        grid.add(keyFilePathField, 1, 3);
        grid.add(browseButton, 2, 3);

        Label explanationLabel = new Label(
            "A security key file will be generated and saved at the location you choose.\n" +
            "You will need both this file and your master password to log in.\n" +
            "Keep this file safe and secure!"
        );
        explanationLabel.setWrapText(true);
        grid.add(explanationLabel, 0, 4, 3, 1);

        getDialogPane().setContent(grid);

        ButtonType createButtonType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        Node createButton = getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        passwordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));
        visiblePasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));
        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));
        visibleConfirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));
        keyFilePathField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));

        setResultConverter(dialogButton -> {
            if (dialogButton == createButtonType) {
                return new SignUpResult(
                    passwordField.getText(),
                    keyFilePathField.getText()
                );
            }
            return null;
        });
    }

    private void validateForm(Node createButton) {
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        boolean isValid = !password.isEmpty() &&
                         password.equals(confirmPassword) &&
                         !keyFilePathField.getText().isEmpty() &&
                         password.length() >= 8;  
        createButton.setDisable(!isValid);

        if (!password.isEmpty() && !password.equals(confirmPassword)) {
            confirmPasswordField.setStyle("-fx-border-color: red;");
            visibleConfirmPasswordField.setStyle("-fx-border-color: red;");
        } else {
            confirmPasswordField.setStyle("");
            visibleConfirmPasswordField.setStyle("");
        }

        if (!password.isEmpty() && password.length() < 8) {
            passwordField.setStyle("-fx-border-color: red;");
            visiblePasswordField.setStyle("-fx-border-color: red;");
        } else {
            passwordField.setStyle("");
            visiblePasswordField.setStyle("");
        }
    }
} 