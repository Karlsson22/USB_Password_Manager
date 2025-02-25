package com.passwordmanager.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.Node;
import java.io.File;

public class SignUpDialog extends Dialog<SignUpResult> {
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private TextField keyFilePathField;
    private Button browseButton;

    public SignUpDialog(Window owner) {
        setTitle("Create Account");
        setHeaderText("Set up your master password and choose where to save your security key file");
        initOwner(owner);

        // Create the form fields
        passwordField = new PasswordField();
        confirmPasswordField = new PasswordField();
        keyFilePathField = new TextField();
        browseButton = new Button("Choose Location");

        // Set up the file chooser for saving the key file
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

        // Create the layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Master Password:"), 0, 0);
        grid.add(passwordField, 1, 0);
        grid.add(new Label("Confirm Password:"), 0, 1);
        grid.add(confirmPasswordField, 1, 1);
        grid.add(new Label("Key File Location:"), 0, 2);
        grid.add(keyFilePathField, 1, 2);
        grid.add(browseButton, 2, 2);

        // Add explanation label
        Label explanationLabel = new Label(
            "A security key file will be generated and saved at the location you choose.\n" +
            "You will need both this file and your master password to log in.\n" +
            "Keep this file safe and secure!"
        );
        explanationLabel.setWrapText(true);
        grid.add(explanationLabel, 0, 3, 3, 1);

        getDialogPane().setContent(grid);

        // Add buttons
        ButtonType createButtonType = new ButtonType("Create Account", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // Enable/Disable create button based on form validation
        Node createButton = getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true);

        // Add validation listeners
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));
        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));
        keyFilePathField.textProperty().addListener((obs, oldVal, newVal) -> validateForm(createButton));

        // Set the result converter
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
        boolean isValid = !passwordField.getText().isEmpty() &&
                         passwordField.getText().equals(confirmPasswordField.getText()) &&
                         !keyFilePathField.getText().isEmpty() &&
                         passwordField.getText().length() >= 8;  // Minimum password length
        createButton.setDisable(!isValid);

        // Show password mismatch warning
        if (!passwordField.getText().isEmpty() && !passwordField.getText().equals(confirmPasswordField.getText())) {
            confirmPasswordField.setStyle("-fx-border-color: red;");
        } else {
            confirmPasswordField.setStyle("");
        }

        // Show password length warning
        if (!passwordField.getText().isEmpty() && passwordField.getText().length() < 8) {
            passwordField.setStyle("-fx-border-color: red;");
        } else {
            passwordField.setStyle("");
        }
    }
} 