package com.passwordmanager.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.stage.Window;
import javafx.scene.Node;
import com.passwordmanager.model.PasswordEntry;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import com.passwordmanager.security.ClipboardManager;
import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;

public class PasswordDetailDialog extends Dialog<PasswordEntry> {
    private TextField titleField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField urlField;
    private TextArea notesArea;
    private TextField categoryField;
    private CheckBox showPasswordCheckBox;
    private TextField visiblePasswordField;

    public PasswordDetailDialog(Window owner, PasswordEntry entry) {
        setTitle("Password Details");
        setHeaderText("View or Edit Password Entry");
        initOwner(owner);

        titleField = new TextField(entry.getTitle());
        usernameField = new TextField(entry.getUsername());
        passwordField = new PasswordField();
        visiblePasswordField = new TextField();
        passwordField.setText(entry.getPassword());
        visiblePasswordField.setText(entry.getPassword());
        urlField = new TextField(entry.getUrl());
        notesArea = new TextArea(entry.getNotes());
        categoryField = new TextField(entry.getCategory());
        showPasswordCheckBox = new CheckBox("Show Password");

        Button copyUsernameButton = new Button("Copy");
        Button copyPasswordButton = new Button("Copy");
        Button copyUrlButton = new Button("Copy");

        visiblePasswordField.setManaged(false);
        visiblePasswordField.setVisible(false);
        showPasswordCheckBox.setOnAction(e -> {
            passwordField.setManaged(!showPasswordCheckBox.isSelected());
            passwordField.setVisible(!showPasswordCheckBox.isSelected());
            visiblePasswordField.setManaged(showPasswordCheckBox.isSelected());
            visiblePasswordField.setVisible(showPasswordCheckBox.isSelected());
        });

        passwordField.textProperty().bindBidirectional(visiblePasswordField.textProperty());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        int row = 0;
        grid.add(new Label("Title:*"), 0, row);
        grid.add(titleField, 1, row);

        row++;
        grid.add(new Label("Username:"), 0, row);
        HBox usernameBox = new HBox(10, usernameField, copyUsernameButton);
        grid.add(usernameBox, 1, row);

        row++;
        grid.add(new Label("Password:*"), 0, row);
        HBox passwordBox = new HBox(10, passwordField, visiblePasswordField, copyPasswordButton);
        grid.add(passwordBox, 1, row);
        
        row++;
        grid.add(showPasswordCheckBox, 1, row);

        row++;
        grid.add(new Label("URL:"), 0, row);
        HBox urlBox = new HBox(10, urlField, copyUrlButton);
        grid.add(urlBox, 1, row);

        row++;
        grid.add(new Label("Category:"), 0, row);
        grid.add(categoryField, 1, row);

        row++;
        grid.add(new Label("Notes:"), 0, row);
        grid.add(notesArea, 1, row);
        notesArea.setPrefRowCount(3);

        setupCopyButtons(copyUsernameButton, copyPasswordButton, copyUrlButton);

        getDialogPane().setContent(grid);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        Node saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(false);
        
        titleField.textProperty().addListener((observable, oldValue, newValue) -> 
            saveButton.setDisable(newValue.trim().isEmpty()));

        setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                confirmDialog.setTitle("Confirm Changes");
                confirmDialog.setHeaderText("Save Changes?");
                confirmDialog.setContentText("Are you sure you want to save the changes to this password entry?");
                
                ButtonType yesButton = new ButtonType("Yes, Save", ButtonBar.ButtonData.YES);
                ButtonType noButton = new ButtonType("No, Cancel", ButtonBar.ButtonData.NO);
                confirmDialog.getButtonTypes().setAll(yesButton, noButton);

                if (confirmDialog.showAndWait().orElse(noButton) == yesButton) {
                    entry.setTitle(titleField.getText());
                    entry.setUsername(usernameField.getText());
                    entry.setPassword(passwordField.getText());
                    entry.setUrl(urlField.getText());
                    entry.setNotes(notesArea.getText());
                    entry.setCategory(categoryField.getText());
                    entry.updateLastModified();
                    return entry;
                }
                return null;
            }
            return null;
        });
    }

    private void setupCopyButtons(Button copyUsernameButton, Button copyPasswordButton, Button copyUrlButton) {
        copyUsernameButton.setOnAction(e -> {
            ClipboardManager.copyToClipboard(usernameField.getText(), true);
            showCopiedNotification("Username copied! Will be cleared in 30 seconds.");
        });

        copyPasswordButton.setOnAction(e -> {
            ClipboardManager.copyToClipboard(passwordField.getText(), true);
            showCopiedNotification("Password copied! Will be cleared in 30 seconds.");
        });

        copyUrlButton.setOnAction(e -> {
            ClipboardManager.copyToClipboard(urlField.getText(), false);
            showCopiedNotification("URL copied!");
        });
    }

    private void showCopiedNotification(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Copied");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
        
        Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> alert.close());
            }
        }, 2000);
    }
} 