package com.passwordmanager.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import com.passwordmanager.model.PasswordEntry;
import com.passwordmanager.database.DatabaseManager;
import com.passwordmanager.security.InputValidator;
import com.passwordmanager.security.InputValidator.ValidationException;
import java.time.Instant;
import java.util.Optional;

public class PasswordEntryController {
    @FXML
    public TextField titleField;
    
    @FXML
    public TextField usernameField;
    
    @FXML
    public PasswordField passwordField;
    
    @FXML
    public TextField urlField;
    
    @FXML
    public TextArea notesArea;
    
    @FXML
    public ComboBox<String> categoryComboBox;
    
    @FXML
    public Button addCategoryButton;
    
    @FXML
    public GridPane gridPane;
    
    @FXML
    public VBox root;
    
    private final DatabaseManager dbManager;
    private PasswordEntry entry;
    private ObservableList<String> categories;

    public PasswordEntryController(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.categories = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        setupCategoryComboBox();
        loadCategories();
    }

    private void setupCategoryComboBox() {
        categoryComboBox.setPromptText("Select or create a category");
        categoryComboBox.setItems(categories);
        categoryComboBox.setEditable(true);
    }

    private void loadCategories() {
        try {
            categories.clear();
            categories.addAll(dbManager.getAllCategories());
        } catch (Exception e) {
            showError("Error", "Failed to load categories: " + e.getMessage());
        }
    }

    @FXML
    public void handleAddCategory() {
        String newCategory = categoryComboBox.getEditor().getText().trim();
        if (!newCategory.isEmpty()) {
            categories.add(newCategory);
            categoryComboBox.setValue(newCategory);
        }
    }

    public void setEntry(PasswordEntry entry) {
        this.entry = entry;
        if (entry != null) {
            titleField.setText(entry.getTitle());
            usernameField.setText(entry.getUsername());
            passwordField.setText(entry.getPassword());
            urlField.setText(entry.getUrl());
            notesArea.setText(entry.getNotes());
            
            if (entry.getCategory() != null) {
                categoryComboBox.setValue(entry.getCategory());
            }
        } else {
            titleField.clear();
            usernameField.clear();
            passwordField.clear();
            urlField.clear();
            notesArea.clear();
            categoryComboBox.setValue(null);
        }
    }

    @FXML
    public void handleSave() {
        try {
            String title = titleField.getText().trim();
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            if (title.isEmpty()) {
                showError("Validation Error", "Title is required");
                titleField.requestFocus();
                return;
            }
            if (username.isEmpty()) {
                showError("Validation Error", "Username is required");
                usernameField.requestFocus();
                return;
            }
            if (password.isEmpty()) {
                showError("Validation Error", "Password is required");
                passwordField.requestFocus();
                return;
            }

            if (entry == null) {
                entry = new PasswordEntry();
            }
            
            entry.setTitle(title);
            entry.setUsername(username);
            entry.setPassword(password);
            entry.setUrl(urlField.getText().trim());
            entry.setNotes(notesArea.getText().trim());
            entry.setCategory(categoryComboBox.getValue());
            entry.setLastModified(Instant.now().toEpochMilli());
            
        } catch (Exception e) {
            showError("Error", "Failed to save password: " + e.getMessage());
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

    public PasswordEntry getEntry() throws ValidationException {
        handleSave();
        if (entry != null) {
            InputValidator.validatePasswordEntry(
                entry.getTitle(),
                entry.getUsername(),
                entry.getPassword(),
                entry.getUrl(),
                entry.getNotes(),
                entry.getCategory()
            );
            return entry;
        }
        throw new ValidationException("No entry data available");
    }
} 