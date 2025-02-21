package com.passwordmanager.ui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.beans.property.SimpleStringProperty;
import com.passwordmanager.database.DatabaseManager;
import com.passwordmanager.model.PasswordEntry;
import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Priority;
import javafx.collections.transformation.FilteredList;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

public class MainWindow {
    private DatabaseManager dbManager;
    private Stage stage;
    private TableView<PasswordEntry> passwordTable;
    private ListView<String> categoryList;
    private ObservableList<PasswordEntry> passwordList;
    private ObservableList<String> categories;

    public MainWindow(DatabaseManager dbManager, Stage stage) {
        this.dbManager = dbManager;
        this.stage = stage;
        this.passwordList = FXCollections.observableArrayList();
    }

    public void show() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // Create toolbar and search area
        VBox topArea = new VBox(10);
        topArea.setPadding(new Insets(5));
        
        HBox toolbar = createToolbar();
        HBox searchBox = createSearchBox();
        
        topArea.getChildren().addAll(toolbar, searchBox);
        mainLayout.setTop(topArea);

        // Create split view
        HBox contentArea = new HBox(10);
        contentArea.setPadding(new Insets(5));

        // Password table on the left
        VBox leftPane = new VBox(5);
        leftPane.setPrefWidth(600);
        HBox.setHgrow(leftPane, Priority.ALWAYS);
        
        passwordTable = createPasswordTable();
        leftPane.getChildren().add(passwordTable);
        VBox.setVgrow(passwordTable, Priority.ALWAYS);

        // Category list on the right
        VBox rightPane = new VBox(5);
        rightPane.setPrefWidth(200);
        
        Label categoryLabel = new Label("Categories");
        categoryLabel.setStyle("-fx-font-weight: bold");
        
        categoryList = new ListView<>();
        categoryList.setPrefWidth(200);
        VBox.setVgrow(categoryList, Priority.ALWAYS);
        
        rightPane.getChildren().addAll(categoryLabel, categoryList);

        contentArea.getChildren().addAll(leftPane, rightPane);
        mainLayout.setCenter(contentArea);

        // Initialize categories
        categories = FXCollections.observableArrayList();
        categories.add("All"); // Add "All" category
        categoryList.setItems(categories);
        
        // Add category selection handler
        categoryList.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    filterByCategory(newValue);
                }
            }
        );

        // Load passwords
        loadPasswords();

        Scene scene = new Scene(mainLayout, 800, 600);
        stage.setTitle("Password Manager - Main Window");
        stage.setScene(scene);
        stage.show();

        // Select "All" category by default
        categoryList.getSelectionModel().select(0);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5));

        Button addButton = new Button("Add");
        Button editButton = new Button("Edit");
        Button deleteButton = new Button("Delete");
        Button deleteCategoryButton = new Button("Delete Category");

        addButton.setOnAction(e -> handleAddPassword());
        editButton.setOnAction(e -> handleEditButtonClick());
        deleteButton.setOnAction(e -> handleDeletePassword());
        deleteCategoryButton.setOnAction(e -> handleDeleteCategory());

        toolbar.getChildren().addAll(addButton, editButton, deleteButton, deleteCategoryButton);
        return toolbar;
    }

    private HBox createSearchBox() {
        HBox searchBox = new HBox(10);
        searchBox.setPadding(new Insets(0, 5, 5, 5));
        
        Label searchLabel = new Label("Search:");
        TextField searchField = new TextField();
        searchField.setPromptText("Search by title...");
        searchField.setPrefWidth(200);
        
        // Add search functionality
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterTable(newValue);
        });
        
        searchBox.getChildren().addAll(searchLabel, searchField);
        return searchBox;
    }

    private TableView<PasswordEntry> createPasswordTable() {
        TableView<PasswordEntry> table = new TableView<>();

        // Create columns
        TableColumn<PasswordEntry, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTitle()));

        TableColumn<PasswordEntry, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(cellData -> {
            String username = cellData.getValue().getUsername();
            return new SimpleStringProperty(username != null ? username : "");
        });

        TableColumn<PasswordEntry, String> urlCol = new TableColumn<>("URL");
        urlCol.setCellValueFactory(cellData -> {
            String url = cellData.getValue().getUrl();
            return new SimpleStringProperty(url != null ? url : "");
        });

        TableColumn<PasswordEntry, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(cellData -> {
            String category = cellData.getValue().getCategory();
            return new SimpleStringProperty(category != null ? category : "");
        });

        // Add double-click handler
        table.setRowFactory(tv -> {
            TableRow<PasswordEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditPassword(row.getItem());
                }
            });
            return row;
        });

        table.getColumns().addAll(titleCol, usernameCol, urlCol, categoryCol);
        table.setItems(passwordList);
        return table;
    }

    private void loadPasswords() {
        try {
            List<PasswordEntry> passwords = dbManager.getAllPasswords();
            passwordList.clear();
            passwordList.addAll(passwords);
            
            // Update categories
            Set<String> uniqueCategories = new HashSet<>();
            uniqueCategories.add("All");
            for (PasswordEntry entry : passwords) {
                if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
                    uniqueCategories.add(entry.getCategory());
                }
            }
            
            categories.clear();
            categories.addAll(uniqueCategories);
            categories.sort(null); // Sort categories alphabetically
            
            // Make sure "All" stays at the top
            if (categories.remove("All")) {
                categories.add(0, "All");
            }
            
            passwordTable.setItems(passwordList);
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Error Loading Passwords", "Failed to load passwords from database.");
        }
    }

    private void handleAddPassword() {
        PasswordEntryDialog dialog = new PasswordEntryDialog(stage);
        dialog.showAndWait().ifPresent(entry -> {
            try {
                dbManager.addPasswordEntry(entry);
                loadPasswords();
                System.out.println("Password entry added successfully!");
            } catch (SQLException e) {
                showError("Error Adding Password", "Failed to add password entry.");
                e.printStackTrace();
            }
        });
    }

    private void handleEditPassword(PasswordEntry entry) {
        PasswordDetailDialog dialog = new PasswordDetailDialog(stage, entry);
        dialog.showAndWait().ifPresent(updatedEntry -> {
            try {
                dbManager.updatePasswordEntry(updatedEntry);
                loadPasswords();  // Refresh the table
                System.out.println("Password entry updated successfully!");
            } catch (SQLException e) {
                showError("Error Updating Password", "Failed to update password entry.");
                e.printStackTrace();
            }
        });
    }

    private void handleDeletePassword() {
        // Get selected password entry
        PasswordEntry selectedEntry = passwordTable.getSelectionModel().getSelectedItem();
        
        if (selectedEntry == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Password Selected");
            alert.setContentText("Please select a password entry to delete.");
            alert.showAndWait();
            return;
        }

        // Show confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Deletion");
        confirmDialog.setHeaderText("Delete Password Entry");
        confirmDialog.setContentText("Are you sure you want to delete the password entry for: " + 
                                   selectedEntry.getTitle() + "?\n\nThis action cannot be undone.");

        // Add custom buttons
        ButtonType deleteButton = new ButtonType("Yes, Delete", ButtonBar.ButtonData.YES);
        ButtonType cancelButton = new ButtonType("No, Cancel", ButtonBar.ButtonData.NO);
        confirmDialog.getButtonTypes().setAll(deleteButton, cancelButton);

        // Show dialog and wait for response
        if (confirmDialog.showAndWait().orElse(cancelButton) == deleteButton) {
            try {
                dbManager.deletePasswordEntry(selectedEntry.getId());
                loadPasswords();  // Refresh the table
                System.out.println("Password entry deleted successfully!");
            } catch (SQLException e) {
                showError("Error Deleting Password", "Failed to delete password entry.");
                e.printStackTrace();
            }
        }
    }

    private void handleEditButtonClick() {
        // Get selected password entry
        PasswordEntry selectedEntry = passwordTable.getSelectionModel().getSelectedItem();
        
        if (selectedEntry == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Password Selected");
            alert.setContentText("Please select a password entry to edit.");
            alert.showAndWait();
            return;
        }

        // Open the edit dialog
        handleEditPassword(selectedEntry);
    }

    private void filterTable(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            passwordTable.setItems(passwordList);
        } else {
            ObservableList<PasswordEntry> filteredList = FXCollections.observableArrayList();
            String lowerCaseFilter = searchText.toLowerCase();
            
            for (PasswordEntry entry : passwordList) {
                if (entry.getTitle().toLowerCase().contains(lowerCaseFilter)) {
                    filteredList.add(entry);
                }
            }
            passwordTable.setItems(filteredList);
        }
    }

    private void filterByCategory(String category) {
        if (category == null || category.equals("All")) {
            passwordTable.setItems(passwordList);
        } else {
            FilteredList<PasswordEntry> filteredData = new FilteredList<>(passwordList);
            filteredData.setPredicate(entry -> 
                category.equals(entry.getCategory())
            );
            passwordTable.setItems(filteredData);
        }
    }

    private void handleDeleteCategory() {
        String selectedCategory = categoryList.getSelectionModel().getSelectedItem();
        
        if (selectedCategory == null || selectedCategory.equals("All")) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Invalid Selection");
            alert.setHeaderText("No Valid Category Selected");
            alert.setContentText("Please select a category to delete (cannot delete 'All').");
            alert.showAndWait();
            return;
        }

        // Show confirmation dialog
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirm Category Deletion");
        confirmDialog.setHeaderText("Delete Category and Associated Passwords");
        confirmDialog.setContentText(
            "Are you sure you want to delete the category '" + selectedCategory + 
            "' and ALL passwords in this category?\n\n" +
            "This action cannot be undone!");

        // Add custom buttons
        ButtonType deleteButton = new ButtonType("Yes, Delete Category", ButtonBar.ButtonData.YES);
        ButtonType cancelButton = new ButtonType("No, Cancel", ButtonBar.ButtonData.NO);
        confirmDialog.getButtonTypes().setAll(deleteButton, cancelButton);

        if (confirmDialog.showAndWait().orElse(cancelButton) == deleteButton) {
            try {
                // Get all passwords in this category
                List<PasswordEntry> toDelete = new ArrayList<>();
                for (PasswordEntry entry : passwordList) {
                    if (selectedCategory.equals(entry.getCategory())) {
                        toDelete.add(entry);
                    }
                }

                // Delete all passwords in the category
                for (PasswordEntry entry : toDelete) {
                    dbManager.deletePasswordEntry(entry.getId());
                }

                // Refresh the view
                loadPasswords();
                categoryList.getSelectionModel().select("All");
                
                // Show success message
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Category Deleted");
                successAlert.setHeaderText(null);
                successAlert.setContentText(
                    "Category '" + selectedCategory + "' and " + 
                    toDelete.size() + " password(s) were successfully deleted.");
                successAlert.show();

            } catch (SQLException e) {
                showError("Error Deleting Category", 
                         "Failed to delete category and its passwords.");
                e.printStackTrace();
            }
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
} 