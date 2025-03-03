package com.passwordmanager.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.io.File;
import com.passwordmanager.security.PasswordHasher;
import com.passwordmanager.model.PasswordEntry;
import java.util.ArrayList;
import java.util.List;
import com.passwordmanager.security.Encryptor;

public class DatabaseManager {
    private static final String DB_NAME = "passwords.db";
    private Connection connection;
    private Encryptor encryptor;
    private int currentUserId = -1;  // Track the current logged-in user

    public DatabaseManager() {
        try {
            boolean isNewDatabase = !new File(DB_NAME).exists();
            
            // Initialize database connection in constructor
            File dbFile = new File(DB_NAME);
            System.out.println("Database location: " + dbFile.getAbsolutePath());
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
            
            if (isNewDatabase) {
                System.out.println("Creating new database...");
                createTables();
            }
        } catch (SQLException e) {
            System.err.println("Error initializing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void initializeDatabase(String masterPassword) throws SQLException {
        try {
            String sql = "SELECT id, salt FROM users";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                boolean userFound = false;
                while (rs.next()) {
                    int userId = rs.getInt("id");
                    String salt = rs.getString("salt");
                    String passwordHash = PasswordHasher.hashPassword(masterPassword, salt);
                    
                    String verifySQL = "SELECT 1 FROM users WHERE id = ? AND master_password_hash = ? AND salt = ?";
                    try (PreparedStatement pstmt = connection.prepareStatement(verifySQL)) {
                        pstmt.setInt(1, userId);
                        pstmt.setString(2, passwordHash);
                        pstmt.setString(3, salt);
                        
                        ResultSet verifyRs = pstmt.executeQuery();
                        if (verifyRs.next()) {
                            currentUserId = userId;  // Set current user
                            encryptor = new Encryptor(masterPassword, salt);
                            System.out.println("Encryptor initialized successfully for user " + userId);
                            userFound = true;
                            break;
                        }
                    }
                }
                
                if (!userFound) {
                    throw new SQLException("No matching user found");
                }
            }
            
            testConnection();
        } catch (Exception e) {
            System.err.println("Error initializing encryptor: " + e.getMessage());
            throw new SQLException("Failed to initialize database", e);
        }
    }

    private void testConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Try to execute a simple query
            ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users");
            System.out.println("Database connection successful!");
            System.out.println("Number of users: " + rs.getInt(1));
            
            // Test if tables were created
            ResultSet tables = connection.getMetaData().getTables(null, null, "%", null);
            System.out.println("\nAvailable tables:");
            while (tables.next()) {
                System.out.println("- " + tables.getString("TABLE_NAME"));
            }
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Create users table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY,
                    master_password_hash TEXT NOT NULL,
                    salt TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Create passwords table with user_id
            statement.execute("""
                CREATE TABLE IF NOT EXISTS passwords (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    username TEXT,
                    password TEXT NOT NULL,
                    url TEXT,
                    notes TEXT,
                    category TEXT,
                    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
            """);
            
            System.out.println("Database tables created successfully");
        }
    }

    // Method to close the connection
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean createUser(String masterPassword) {
        try {
            // Generate salt and hash password
            String salt = PasswordHasher.generateSalt();
            String passwordHash = PasswordHasher.hashPassword(masterPassword, salt);

            // Insert new user
            String sql = "INSERT INTO users (master_password_hash, salt) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, passwordHash);
                pstmt.setString(2, salt);
                int result = pstmt.executeUpdate();
                System.out.println("User created successfully!");
                return result > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean verifyMasterPassword(String masterPassword) {
        try {
            String sql = "SELECT master_password_hash, salt FROM users";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    String storedHash = rs.getString("master_password_hash");
                    String salt = rs.getString("salt");
                    String calculatedHash = PasswordHasher.hashPassword(masterPassword, salt);
                    if (storedHash.equals(calculatedHash)) {
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error verifying master password: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public Connection getConnection() {
        return connection;
    }

    public void addPasswordEntry(PasswordEntry entry) throws SQLException {
        if (encryptor == null || currentUserId == -1) {
            throw new SQLException("Not logged in. Please log in first.");
        }

        try {
            String sql = """
                INSERT INTO passwords (user_id, title, username, password, url, notes, category)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, currentUserId);
                pstmt.setString(2, entry.getTitle());
                pstmt.setString(3, encryptor.encrypt(entry.getUsername()));
                pstmt.setString(4, encryptor.encrypt(entry.getPassword()));
                pstmt.setString(5, encryptor.encrypt(entry.getUrl()));
                pstmt.setString(6, encryptor.encrypt(entry.getNotes()));
                pstmt.setString(7, entry.getCategory());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new SQLException("Error encrypting data", e);
        }
    }

    public List<PasswordEntry> getAllPasswords() throws SQLException {
        if (encryptor == null || currentUserId == -1) {
            throw new SQLException("Not logged in. Please log in first.");
        }

        List<PasswordEntry> passwords = new ArrayList<>();
        String sql = "SELECT * FROM passwords WHERE user_id = ? ORDER BY title";
        
        try {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, currentUserId);
                ResultSet rs = pstmt.executeQuery();
                
                while (rs.next()) {
                    PasswordEntry entry = new PasswordEntry();
                    entry.setId(rs.getInt("id"));
                    entry.setTitle(rs.getString("title"));
                    
                    String encryptedUsername = rs.getString("username");
                    String encryptedPassword = rs.getString("password");
                    
                    entry.setUsername(encryptedUsername != null ? encryptor.decrypt(encryptedUsername) : "");
                    entry.setPassword(encryptedPassword != null ? encryptor.decrypt(encryptedPassword) : "");
                    entry.setUrl(rs.getString("url") != null ? encryptor.decrypt(rs.getString("url")) : "");
                    entry.setNotes(rs.getString("notes") != null ? encryptor.decrypt(rs.getString("notes")) : "");
                    entry.setCategory(rs.getString("category"));
                    entry.setLastModified(rs.getLong("last_modified"));
                    passwords.add(entry);
                }
            }
        } catch (Exception e) {
            throw new SQLException("Error decrypting data", e);
        }
        return passwords;
    }

    public void updatePasswordEntry(PasswordEntry entry) throws SQLException {
        try {
            String sql = """
                UPDATE passwords 
                SET title = ?, username = ?, password = ?, url = ?, notes = ?, 
                    category = ?, last_modified = ? 
                WHERE id = ?
            """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, entry.getTitle());
                pstmt.setString(2, encryptor.encrypt(entry.getUsername()));
                pstmt.setString(3, encryptor.encrypt(entry.getPassword()));
                pstmt.setString(4, encryptor.encrypt(entry.getUrl()));
                pstmt.setString(5, encryptor.encrypt(entry.getNotes()));
                pstmt.setString(6, entry.getCategory());
                pstmt.setLong(7, entry.getLastModified());
                pstmt.setInt(8, entry.getId());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new SQLException("Error encrypting data", e);
        }
    }

    public void deletePasswordEntry(int id) throws SQLException {
        String sql = "DELETE FROM passwords WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    private void migrateUnencryptedData() {
        try {
            // Get all passwords
            String sql = "SELECT * FROM passwords";
            List<PasswordEntry> entries = new ArrayList<>();
            
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    PasswordEntry entry = new PasswordEntry();
                    entry.setId(rs.getInt("id"));
                    entry.setTitle(rs.getString("title"));
                    entry.setUsername(rs.getString("username"));
                    entry.setPassword(rs.getString("password"));
                    entry.setUrl(rs.getString("url"));
                    entry.setNotes(rs.getString("notes"));
                    entry.setCategory(rs.getString("category"));
                    entry.setLastModified(rs.getLong("last_modified"));
                    entries.add(entry);
                }
            }

            // Update each entry with encrypted data
            String updateSql = """
                UPDATE passwords 
                SET username = ?, password = ?, url = ?, notes = ?
                WHERE id = ?
            """;
            
            for (PasswordEntry entry : entries) {
                try (PreparedStatement pstmt = connection.prepareStatement(updateSql)) {
                    pstmt.setString(1, encryptor.encrypt(entry.getUsername()));
                    pstmt.setString(2, encryptor.encrypt(entry.getPassword()));
                    pstmt.setString(3, encryptor.encrypt(entry.getUrl()));
                    pstmt.setString(4, encryptor.encrypt(entry.getNotes()));
                    pstmt.setInt(5, entry.getId());
                    pstmt.executeUpdate();
                }
            }
            
            System.out.println("Data migration completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during data migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deleteUser() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Delete all data when deleting user
            stmt.execute("DELETE FROM passwords");
            stmt.execute("DELETE FROM users");
            System.out.println("User and all associated data deleted.");
        }
    }

    public void wipeDatabase() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Drop all tables
            stmt.execute("DROP TABLE IF EXISTS passwords");
            stmt.execute("DROP TABLE IF EXISTS users");
            System.out.println("Database wiped clean.");
            
            // Recreate tables
            createTables();
        }
    }

    public void deleteCurrentUser() throws SQLException {
        if (currentUserId == -1) {
            throw new SQLException("No user is currently logged in");
        }

        try (PreparedStatement pstmt = connection.prepareStatement(
            "DELETE FROM passwords WHERE user_id = ?")) {
            pstmt.setInt(1, currentUserId);
            pstmt.executeUpdate();
        }

        try (PreparedStatement pstmt = connection.prepareStatement(
            "DELETE FROM users WHERE id = ?")) {
            pstmt.setInt(1, currentUserId);
            pstmt.executeUpdate();
        }

        System.out.println("User and all associated data deleted.");
        currentUserId = -1;
        encryptor = null;
    }

    public List<String> getAllCategories() throws SQLException {
        if (currentUserId == -1) {
            throw new SQLException("Not logged in. Please log in first.");
        }

        List<String> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM passwords WHERE user_id = ? AND category IS NOT NULL AND category != '' ORDER BY category";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, currentUserId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                String category = rs.getString("category");
                if (category != null && !category.isEmpty()) {
                    categories.add(category);
                }
            }
        }
        return categories;
    }
} 