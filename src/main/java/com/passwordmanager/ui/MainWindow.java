package com.passwordmanager.ui;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import com.passwordmanager.database.DatabaseManager;
import com.passwordmanager.model.PasswordEntry;
import com.passwordmanager.App;
import com.passwordmanager.security.InputValidator;
import com.passwordmanager.security.InputValidator.ValidationException;
import java.sql.SQLException;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.layout.Priority;
import javafx.collections.transformation.FilteredList;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.io.IOException;
import com.passwordmanager.controller.PasswordEntryController;
import com.passwordmanager.backup.BackupManager;
import javafx.stage.FileChooser;
import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import javafx.geometry.Pos;

public class MainWindow {
    private DatabaseManager dbManager;
    private Stage stage;
    private TableView<PasswordEntry> passwordTable;
    private ListView<String> categoryList;
    private ObservableList<PasswordEntry> passwordList;
    private ObservableList<String> categories;
    private static final String BUTTON_STYLE = """
        -fx-background-color: white;
        -fx-text-fill: #2C3E50;
        -fx-font-size: 13px;
        -fx-padding: 8 15;
        -fx-border-color: #E0E0E0;
        -fx-border-radius: 4;
        -fx-background-radius: 4;
        -fx-cursor: hand;
        """;
    
    private static final String BUTTON_HOVER_STYLE = """
        -fx-background-color: #F8F9FA;
        -fx-text-fill: #2C3E50;
        -fx-font-size: 13px;
        -fx-padding: 8 15;
        -fx-border-color: #0A84FF;
        -fx-border-radius: 4;
        -fx-background-radius: 4;
        -fx-cursor: hand;
        """;
        
    private static final String DANGER_BUTTON_STYLE = """
        -fx-background-color: white;
        -fx-text-fill: #DC3545;
        -fx-font-size: 13px;
        -fx-padding: 8 15;
        -fx-border-color: #DC3545;
        -fx-border-radius: 4;
        -fx-background-radius: 4;
        -fx-cursor: hand;
        """;
        
    private static final String DANGER_BUTTON_HOVER_STYLE = """
        -fx-background-color: #DC3545;
        -fx-text-fill: white;
        -fx-font-size: 13px;
        -fx-padding: 8 15;
        -fx-border-color: #DC3545;
        -fx-border-radius: 4;
        -fx-background-radius: 4;
        -fx-cursor: hand;
        """;

    public MainWindow(DatabaseManager dbManager, Stage stage) {
        this.dbManager = dbManager;
        this.stage = stage;
        this.passwordList = FXCollections.observableArrayList();
    }

    public void show() {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: white;");
        mainLayout.setPadding(new Insets(15));

        VBox sidebar = createSidebar();
        mainLayout.setLeft(sidebar);

        VBox contentArea = createContentArea();
        mainLayout.setCenter(contentArea);

        loadPasswords();

        Scene scene = new Scene(mainLayout, 1000, 600);
        stage.setTitle("The Password Vault - Dashboard");
        stage.setScene(scene);
        stage.show();

        categoryList.getSelectionModel().select(0);
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.setStyle("""
            -fx-background-color: #F8F9FA;
            -fx-padding: 15;
            -fx-border-color: #E0E0E0;
            -fx-border-width: 0 1 0 0;
            """);
        sidebar.setPrefWidth(200);

        Label categoriesHeader = new Label("Categories");
        categoriesHeader.setStyle("""
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-text-fill: #2C3E50;
            """);

        categoryList = new ListView<>();
        categoryList.setStyle("""
            -fx-background-color: transparent;
            -fx-border-color: transparent;
            """);
        VBox.setVgrow(categoryList, Priority.ALWAYS);

        categories = FXCollections.observableArrayList();
        categories.add("All");
        categoryList.setItems(categories);

        categoryList.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    filterByCategory(newValue);
                }
            }
        );

        Button addButton = createStyledButton("+ New Password", false);
        Button backupButton = createStyledButton("Backup", false);
        Button restoreButton = createStyledButton("Restore", false);
        Button logoutButton = createStyledButton("Logout", true);
        Button deleteAccountButton = createStyledButton("Delete Account", true);

        addButton.setOnAction(e -> handleAddPassword());
        backupButton.setOnAction(e -> handleBackup());
        restoreButton.setOnAction(e -> handleRestore());
        logoutButton.setOnAction(e -> handleLogout());
        deleteAccountButton.setOnAction(e -> handleDeleteAccount());

        Separator accountSeparator = new Separator();
        accountSeparator.setPadding(new Insets(5, 0, 5, 0));

        sidebar.getChildren().addAll(
            categoriesHeader,
            categoryList,
            new Separator(),
            addButton,
            backupButton,
            restoreButton,
            accountSeparator,
            deleteAccountButton,
            logoutButton
        );

        return sidebar;
    }

    private VBox createContentArea() {
        VBox contentArea = new VBox(15);
        contentArea.setPadding(new Insets(0, 0, 0, 15));

        HBox searchBox = createSearchBox();

        passwordTable = createPasswordTable();
        VBox.setVgrow(passwordTable, Priority.ALWAYS);

        HBox actionButtons = new HBox(10);
        Button editButton = createStyledButton("Edit", false);
        Button deleteButton = createStyledButton("Delete", true);
        Button deleteCategoryButton = createStyledButton("Delete Category", true);

        editButton.setOnAction(e -> handleEditButtonClick());
        deleteButton.setOnAction(e -> handleDeletePassword());
        deleteCategoryButton.setOnAction(e -> handleDeleteCategory());

        actionButtons.getChildren().addAll(editButton, deleteButton, deleteCategoryButton);

        contentArea.getChildren().addAll(searchBox, passwordTable, actionButtons);
        return contentArea;
    }

    private Button createStyledButton(String text, boolean isDanger) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        
        String normalStyle = isDanger ? DANGER_BUTTON_STYLE : BUTTON_STYLE;
        String hoverStyle = isDanger ? DANGER_BUTTON_HOVER_STYLE : BUTTON_HOVER_STYLE;
        
        button.setStyle(normalStyle);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(normalStyle));
        
        return button;
    }

    private HBox createSearchBox() {
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        TextField searchField = new TextField();
        searchField.setPromptText("Search passwords...");
        searchField.setPrefWidth(300);
        searchField.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #E0E0E0;
            -fx-border-radius: 4;
            -fx-padding: 8 12;
            -fx-font-size: 13px;
            """);
        
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterTable(newValue);
        });
        
        searchBox.getChildren().add(searchField);
        return searchBox;
    }

    private TableView<PasswordEntry> createPasswordTable() {
        TableView<PasswordEntry> table = new TableView<>();
        table.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #E0E0E0;
            -fx-border-radius: 4;
            """);

        TableColumn<PasswordEntry, String> titleCol = new TableColumn<>("Title");
        TableColumn<PasswordEntry, String> usernameCol = new TableColumn<>("Username");
        TableColumn<PasswordEntry, String> urlCol = new TableColumn<>("URL");
        TableColumn<PasswordEntry, String> categoryCol = new TableColumn<>("Category");

        String columnStyle = "-fx-alignment: CENTER-LEFT; -fx-padding: 10;";
        titleCol.setStyle(columnStyle);
        usernameCol.setStyle(columnStyle);
        urlCol.setStyle(columnStyle);
        categoryCol.setStyle(columnStyle);

        titleCol.setPrefWidth(200);
        usernameCol.setPrefWidth(150);
        urlCol.setPrefWidth(200);
        categoryCol.setPrefWidth(150);

        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTitle()));
        usernameCol.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().getUsername() != null ? cellData.getValue().getUsername() : ""));
        urlCol.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().getUrl() != null ? cellData.getValue().getUrl() : ""));
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(
            cellData.getValue().getCategory() != null ? cellData.getValue().getCategory() : ""));

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
            
            Set<String> uniqueCategories = new HashSet<>();
            uniqueCategories.add("All");
            for (PasswordEntry entry : passwords) {
                if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
                    uniqueCategories.add(entry.getCategory());
                }
            }
            
            categories.clear();
            categories.addAll(uniqueCategories);
            categories.sort(null);
            
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
        try {
            FXMLLoader loader = new FXMLLoader(PasswordEntryController.class.getResource("/fxml/password_entry.fxml"));
            PasswordEntryController controller = new PasswordEntryController(dbManager);
            loader.setController(controller);
            Parent root = loader.load();
            
            Dialog<PasswordEntry> dialog = new Dialog<>();
            dialog.setTitle("Add Password Entry");
            dialog.setHeaderText(null);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
            okButton.setDisable(false);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    try {
                        return controller.getEntry();
                    } catch (ValidationException e) {
                        showError("Validation Error", e.getMessage());
                        return null;
                    }
                }
                return null;
            });
            
            dialog.showAndWait().ifPresent(entry -> {
                if (entry != null) {
                    try {
                        dbManager.addPasswordEntry(entry);
                        loadPasswords();
                    } catch (SQLException | ValidationException e) {
                        showError("Error", "Failed to add password entry: " + e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            showError("Error", "Failed to load password entry dialog: " + e.getMessage());
        }
    }

    private void handleEditPassword(PasswordEntry entry) {
        try {
            FXMLLoader loader = new FXMLLoader(PasswordEntryController.class.getResource("/fxml/password_entry.fxml"));
            PasswordEntryController controller = new PasswordEntryController(dbManager);
            loader.setController(controller);
            Parent root = loader.load();
            controller.setEntry(entry);
            
            Dialog<PasswordEntry> dialog = new Dialog<>();
            dialog.setTitle("Edit Password Entry");
            dialog.setHeaderText(null);
            dialog.getDialogPane().setContent(root);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    try {
                        return controller.getEntry();
                    } catch (ValidationException e) {
                        showError("Validation Error", e.getMessage());
                        return null;
                    }
                }
                return null;
            });
            
            dialog.showAndWait().ifPresent(updatedEntry -> {
                if (updatedEntry != null) {
                    try {
                        dbManager.updatePasswordEntry(updatedEntry);
                        loadPasswords();
                    } catch (SQLException | ValidationException e) {
                        showError("Error", "Failed to update password entry: " + e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            showError("Error", "Failed to load password entry dialog: " + e.getMessage());
        }
    }

    private void handleDeletePassword() {
        PasswordEntry selectedEntry = passwordTable.getSelectionModel().getSelectedItem();
        
        if (selectedEntry == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Password Selected");
            alert.setContentText("Please select a password entry to delete.");
            alert.showAndWait();
            return;
        }

        Dialog<Boolean> confirmDialog = new Dialog<>();
        confirmDialog.setTitle("Confirm Deletion");
        confirmDialog.setHeaderText("Delete Password Entry");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        grid.add(new Label("Are you sure you want to delete this entry?"), 0, 0);
        grid.add(new Label("Title: " + selectedEntry.getTitle()), 0, 1);
        grid.add(new Label("This action cannot be undone."), 0, 2);
        
        confirmDialog.getDialogPane().setContent(grid);
        
        ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getDialogPane().getButtonTypes().addAll(deleteButton, cancelButton);
        
        confirmDialog.setResultConverter(dialogButton -> dialogButton == deleteButton);

        confirmDialog.showAndWait().ifPresent(confirmed -> {
            if (confirmed) {
                try {
                    int entryId = selectedEntry.getId();
                    String entryTitle = selectedEntry.getTitle();
                    
                    selectedEntry.setPassword(null);
                    selectedEntry.setUsername(null);
                    selectedEntry.setNotes(null);
                    
                    dbManager.deletePasswordEntry(entryId);
                    
                    passwordTable.getSelectionModel().clearSelection();
                    loadPasswords();
                    
                    System.out.println("Password entry '" + entryTitle + "' deleted successfully");
                } catch (SQLException e) {
                    showError("Error Deleting Password", "Failed to delete password entry: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    System.gc();
                }
            }
        });
    }

    private void handleEditButtonClick() {
        PasswordEntry selectedEntry = passwordTable.getSelectionModel().getSelectedItem();
        
        if (selectedEntry == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Selection");
            alert.setHeaderText("No Password Selected");
            alert.setContentText("Please select a password entry to edit.");
            alert.showAndWait();
            return;
        }

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

        Dialog<Boolean> confirmDialog = new Dialog<>();
        confirmDialog.setTitle("Confirm Category Deletion");
        confirmDialog.setHeaderText("Delete Category and Associated Passwords");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        grid.add(new Label("Are you sure you want to delete the category '" + selectedCategory + "'?"), 0, 0);
        grid.add(new Label("WARNING: This will delete ALL passwords in this category!"), 0, 1);
        grid.add(new Label("This action cannot be undone."), 0, 2);
        
        confirmDialog.getDialogPane().setContent(grid);
        
        ButtonType deleteButton = new ButtonType("Delete Category", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getDialogPane().getButtonTypes().addAll(deleteButton, cancelButton);
        
        confirmDialog.setResultConverter(dialogButton -> dialogButton == deleteButton);

        confirmDialog.showAndWait().ifPresent(confirmed -> {
            if (confirmed) {
                try {
                    List<PasswordEntry> toDelete = new ArrayList<>();
                    for (PasswordEntry entry : passwordList) {
                        if (selectedCategory.equals(entry.getCategory())) {
                            toDelete.add(entry);
                        }
                    }

                    int deletedCount = 0;
                    for (PasswordEntry entry : toDelete) {
                        entry.setPassword(null);
                        entry.setUsername(null);
                        entry.setNotes(null);
                        
                        dbManager.deletePasswordEntry(entry.getId());
                        deletedCount++;
                    }

                    categoryList.getSelectionModel().select("All");
                    loadPasswords();
                    
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Category Deleted");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText(
                        "Category '" + selectedCategory + "' and " + 
                        deletedCount + " password(s) were successfully deleted.");
                    successAlert.show();
                    
                    System.out.println("Category '" + selectedCategory + "' deleted with " + deletedCount + " entries");
                } catch (SQLException e) {
                    showError("Error Deleting Category", 
                             "Failed to delete category and its passwords: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    System.gc();
                }
            }
        });
    }

    private void handleLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Log Out");
        confirm.setHeaderText("Are you sure you want to log out?");
        confirm.setContentText("Any unsaved changes will be lost.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    stage.close();
                    showLoginScreen();
                } catch (Exception ex) {
                    showError("Logout Failed", 
                        "Failed to log out: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
    }

    private void showLoginScreen() {
        try {
            Stage loginStage = new Stage();
            App app = new App();
            app.start(loginStage);
        } catch (Exception e) {
            showError("Error", 
                "Failed to return to login screen: " + e.getMessage());
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

    private void handleBackup() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Database Backup");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Password Manager Backup", "*.pmbackup")
        );
        
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        );
        fileChooser.setInitialFileName("backup_" + timestamp);
        
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                SecretKey masterKey = dbManager.getMasterKey();
                
                BackupManager backupManager = new BackupManager(
                    dbManager.getDatabasePath(),
                    masterKey
                );
                
                backupManager.createBackup(file.getPath());
                
                showInfo("Backup Created", 
                    "Database backup has been created successfully!\n" +
                    "Location: " + file.getPath());
                
            } catch (Exception ex) {
                showError("Backup Failed", 
                    "Failed to create backup: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void handleRestore() {
        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Restore Database");
        confirm.setHeaderText("Are you sure you want to restore from backup?");
        confirm.setContentText(
            "This will replace your current database with the backup.\n" +
            "All current data will be lost!\n\n" +
            "Make sure you have a backup of your current database if needed."
        );
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Select Backup File");
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Password Manager Backup", "*.pmbackup")
                );
                
                File file = fileChooser.showOpenDialog(stage);
                if (file != null) {
                    try {
                        SecretKey masterKey = dbManager.getMasterKey();
                        
                        BackupManager backupManager = new BackupManager(
                            dbManager.getDatabasePath(),
                            masterKey
                        );
                        
                        dbManager.closeConnection();
                        
                        backupManager.restoreBackup(
                            file.getPath(),
                            dbManager.getDatabasePath()
                        );
                        
                        showInfo("Restore Successful", 
                            "Database has been restored successfully!\n" +
                            "The application will now restart.");
                        
                        restartApplication();
                        
                    } catch (Exception ex) {
                        showError("Restore Failed", 
                            "Failed to restore backup: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
            }
        });
    }

    private void restartApplication() {
        stage.close();
        
        try {
            Stage loginStage = new Stage();
            App app = new App();
            app.start(loginStage);
        } catch (Exception e) {
            showError("Restart Failed", 
                "Failed to restart application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void handleDeleteAccount() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Delete Account");
        dialog.setHeaderText("Are you sure you want to delete your account?\n" +
                           "This will permanently delete all your saved passwords!\n\n" +
                           "Type 'delete' to confirm:");

        DialogPane dialogPane = dialog.getDialogPane();
        TextField confirmField = new TextField();
        confirmField.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #E0E0E0;
            -fx-border-radius: 4;
            -fx-padding: 8 12;
            -fx-font-size: 13px;
            """);
        dialogPane.setContent(confirmField);

        ButtonType deleteButtonType = new ButtonType("Delete Account", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(deleteButtonType, ButtonType.CANCEL);

        dialogPane.setStyle("""
            -fx-background-color: white;
            -fx-padding: 20;
            """);

        Button deleteButton = (Button) dialogPane.lookupButton(deleteButtonType);
        deleteButton.setStyle(DANGER_BUTTON_STYLE);
        deleteButton.setOnMouseEntered(e -> deleteButton.setStyle(DANGER_BUTTON_HOVER_STYLE));
        deleteButton.setOnMouseExited(e -> deleteButton.setStyle(DANGER_BUTTON_STYLE));

        deleteButton.setDisable(true);

        confirmField.textProperty().addListener((observable, oldValue, newValue) -> {
            deleteButton.setDisable(!newValue.equals("delete"));
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == deleteButtonType) {
                return confirmField.getText();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result.equals("delete")) {
                try {
                    dbManager.deleteCurrentUser();
                    stage.close();
                    showLoginScreen();
                } catch (SQLException ex) {
                    showError("Delete Failed", 
                        "Failed to delete account: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
    }
} 