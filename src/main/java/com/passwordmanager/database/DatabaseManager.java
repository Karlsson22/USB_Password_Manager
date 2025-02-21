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

    public void initializeDatabase(String masterPassword) {
        try {
            // Print the absolute path where database will be created
            File dbFile = new File(DB_NAME);
            System.out.println("Database location: " + dbFile.getAbsolutePath());
            
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
            createTables();
            
            // Initialize encryptor with master password and salt
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT salt FROM users LIMIT 1");
                if (rs.next()) {
                    String salt = rs.getString("salt");
                    encryptor = new Encryptor(masterPassword, salt);
                    System.out.println("Encryptor initialized successfully");
                } else {
                    System.err.println("No salt found in users table");
                }
            }
            
            // Test the connection
            testConnection();
        } catch (Exception e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
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

            // Create passwords table
            statement.execute("""
                CREATE TABLE IF NOT EXISTS passwords (
                    id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    username TEXT,
                    password TEXT NOT NULL,
                    url TEXT,
                    notes TEXT,
                    category TEXT,
                    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
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
            // Check if any user exists
            try (Statement checkStmt = connection.createStatement()) {
                ResultSet rs = checkStmt.executeQuery("SELECT COUNT(*) FROM users");
                if (rs.getInt(1) > 0) {
                    System.out.println("User already exists!");
                    return false;
                }
            }

            // Generate salt and hash password
            String salt = PasswordHasher.generateSalt();
            String passwordHash = PasswordHasher.hashPassword(masterPassword, salt);

            // Insert new user
            String sql = "INSERT INTO users (master_password_hash, salt) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, passwordHash);
                pstmt.setString(2, salt);
                pstmt.executeUpdate();
                System.out.println("User created successfully!");
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean verifyMasterPassword(String masterPassword) {
        try {
            // Get user's salt and password hash
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT master_password_hash, salt FROM users LIMIT 1");
                if (rs.next()) {
                    String storedHash = rs.getString("master_password_hash");
                    String salt = rs.getString("salt");
                    
                    // Hash the provided password with the stored salt
                    String providedHash = PasswordHasher.hashPassword(masterPassword, salt);
                    
                    // Compare the hashes
                    return storedHash.equals(providedHash);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Connection getConnection() {
        return connection;
    }

    public void addPasswordEntry(PasswordEntry entry) throws SQLException {
        if (encryptor == null) {
            throw new SQLException("Encryptor not initialized. Please log in first.");
        }

        try {
            String sql = """
                INSERT INTO passwords (title, username, password, url, notes, category)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, entry.getTitle());
                pstmt.setString(2, encryptor.encrypt(entry.getUsername()));
                pstmt.setString(3, encryptor.encrypt(entry.getPassword()));
                pstmt.setString(4, encryptor.encrypt(entry.getUrl()));
                pstmt.setString(5, encryptor.encrypt(entry.getNotes()));
                pstmt.setString(6, entry.getCategory());
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new SQLException("Error encrypting data", e);
        }
    }

    public List<PasswordEntry> getAllPasswords() throws SQLException {
        if (encryptor == null) {
            throw new SQLException("Encryptor not initialized. Please log in first.");
        }

        List<PasswordEntry> passwords = new ArrayList<>();
        String sql = "SELECT * FROM passwords ORDER BY title";
        
        try {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
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
} 