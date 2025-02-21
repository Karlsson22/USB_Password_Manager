package com.passwordmanager.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import javafx.scene.Node;
import com.passwordmanager.model.PasswordEntry;

public class PasswordEntryDialog extends Dialog<PasswordEntry> {
    private TextField titleField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField urlField;
    private TextArea notesArea;
    private TextField categoryField;
    private Button generateButton;

    public PasswordEntryDialog(Window owner) {
        setTitle("Add Password Entry");
        setHeaderText("Enter password details");
        initOwner(owner);

        // Create the form fields
        titleField = new TextField();
        usernameField = new TextField();
        passwordField = new PasswordField();
        urlField = new TextField();
        notesArea = new TextArea();
        categoryField = new TextField();
        generateButton = new Button("Generate");

        // Password field with generate button
        HBox passwordBox = new HBox(10);
        passwordBox.getChildren().addAll(passwordField, generateButton);

        // Create the layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Title:*"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Password:*"), 0, 2);
        grid.add(passwordBox, 1, 2);
        grid.add(new Label("URL:"), 0, 3);
        grid.add(urlField, 1, 3);
        grid.add(new Label("Category:"), 0, 4);
        grid.add(categoryField, 1, 4);
        grid.add(new Label("Notes:"), 0, 5);
        grid.add(notesArea, 1, 5);

        notesArea.setPrefRowCount(3);

        getDialogPane().setContent(grid);

        // Add buttons
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Enable/Disable save button depending on title field
        Node saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        titleField.textProperty().addListener((observable, oldValue, newValue) -> 
            saveButton.setDisable(newValue.trim().isEmpty()));

        // Convert the result to PasswordEntry when save is clicked
        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new PasswordEntry(
                    titleField.getText(),
                    usernameField.getText(),
                    passwordField.getText(),
                    urlField.getText(),
                    notesArea.getText(),
                    categoryField.getText()
                );
            }
            return null;
        });

        // Add password generation functionality
        generateButton.setOnAction(e -> passwordField.setText(generatePassword()));
    }

    private String generatePassword() {
        // Simple password generator - you might want to make this more sophisticated
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int index = (int) (Math.random() * chars.length());
            password.append(chars.charAt(index));
        }
        return password.toString();
    }
} 