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
import javax.crypto.SecretKey;
import com.passwordmanager.security.SaltManager;
import com.passwordmanager.security.InputValidator;
import com.passwordmanager.security.InputValidator.ValidationException;
import com.passwordmanager.security.SecureWiper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseManager {
    private static final String DB_NAME = "passwords.db";
    private Connection connection;
    private Encryptor encryptor;
    private int currentUserId = -1;

    public DatabaseManager() {
        try {
            boolean isNewDatabase = !new File(DB_NAME).exists();
            
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
            String sql = "SELECT id, current_salt, encrypted_dek FROM users";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                boolean userFound = false;
                while (rs.next()) {
                    int userId = rs.getInt("id");
                    String salt = rs.getString("current_salt");
                    String encryptedDEK = rs.getString("encrypted_dek");
                    
                    try {
                        SecretKey kek = Encryptor.deriveKEK(masterPassword, salt);
                        
                        SecretKey dek = Encryptor.decryptDEK(encryptedDEK, kek);
                        
                        currentUserId = userId;
                        encryptor = new Encryptor(dek);
                        
                        SaltManager saltManager = new SaltManager(connection, userId, masterPassword);
                        saltManager.rotateSaltIfNeeded();
                        
                        System.out.println("Encryptor initialized successfully for user " + userId);
                        userFound = true;
                        break;
                    } catch (Exception e) {
                        continue;
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
            ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM users");
            System.out.println("Database connection successful!");
            System.out.println("Number of users: " + rs.getInt(1));
            
            ResultSet tables = connection.getMetaData().getTables(null, null, "%", null);
            System.out.println("\nAvailable tables:");
            while (tables.next()) {
                System.out.println("- " + tables.getString("TABLE_NAME"));
            }
        }
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Create users table with support for envelope encryption and salt rotation
            statement.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY,
                    master_password_hash TEXT NOT NULL,
                    current_salt TEXT NOT NULL,
                    encrypted_dek TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Create salt history table for salt rotation
            statement.execute("""
                CREATE TABLE IF NOT EXISTS salt_history (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER NOT NULL,
                    salt TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    retired_at TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id)
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

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                cleanupTempFiles();
                
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private void cleanupTempFiles() throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        File tempFolder = new File(tempDir, "passwordmanager_temp");
        
        if (tempFolder.exists()) {
            SecureWiper.secureTempCleanup(tempFolder);
        }
    }

    public boolean createUser(String masterPassword) throws ValidationException {
        if (!InputValidator.isValidMasterPassword(masterPassword)) {
            throw new ValidationException("Invalid master password format. Password must be at least 12 characters long and contain uppercase, lowercase, numbers, and special characters.");
        }

        try {
            masterPassword = InputValidator.sanitizeInput(masterPassword);
            
            SaltManager saltManager = new SaltManager(connection, -1, masterPassword);
            String salt = saltManager.generateNewSalt();
            
            SecretKey dek = Encryptor.generateDEK();
            
            SecretKey kek = Encryptor.deriveKEK(masterPassword, salt);
            
            String encryptedDEK = Encryptor.encryptDEK(dek, kek);
            
            String sql = "INSERT INTO users (master_password_hash, current_salt, encrypted_dek) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                String passwordHash = PasswordHasher.hashPassword(masterPassword, salt);
                pstmt.setString(1, passwordHash);
                pstmt.setString(2, salt);
                pstmt.setString(3, encryptedDEK);
                
                int result = pstmt.executeUpdate();
                
                if (result > 0) {
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            int userId = rs.getInt(1);
                            String historySql = "INSERT INTO salt_history (user_id, salt, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
                            try (PreparedStatement historyStmt = connection.prepareStatement(historySql)) {
                                historyStmt.setInt(1, userId);
                                historyStmt.setString(2, salt);
                                historyStmt.executeUpdate();
                            }
                        }
                    }
                    
                    System.out.println("User created successfully!");
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Error creating user: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean verifyMasterPassword(String masterPassword) {
        try {
            String sql = "SELECT master_password_hash, current_salt, encrypted_dek FROM users";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    String storedHash = rs.getString("master_password_hash");
                    String salt = rs.getString("current_salt");
                    String encryptedDEK = rs.getString("encrypted_dek");

                    String calculatedHash = PasswordHasher.hashPassword(masterPassword, salt);
                    if (storedHash.equals(calculatedHash)) {
                        try {
                            SecretKey kek = Encryptor.deriveKEK(masterPassword, salt);
                            Encryptor.decryptDEK(encryptedDEK, kek);
                            return true;
                        } catch (Exception e) {
                            continue;
                        }
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

    public void addPasswordEntry(PasswordEntry entry) throws SQLException, ValidationException {
        if (encryptor == null || currentUserId == -1) {
            throw new SQLException("Not logged in. Please log in first.");
        }

        InputValidator.validatePasswordEntry(
            entry.getTitle(),
            entry.getUsername(),
            entry.getPassword(),
            entry.getUrl(),
            entry.getNotes(),
            entry.getCategory()
        );

        entry.setTitle(InputValidator.sanitizeInput(entry.getTitle()));
        entry.setUsername(InputValidator.sanitizeInput(entry.getUsername()));
        entry.setPassword(InputValidator.sanitizeInput(entry.getPassword()));
        entry.setUrl(InputValidator.sanitizeInput(entry.getUrl()));
        entry.setNotes(InputValidator.sanitizeInput(entry.getNotes()));
        entry.setCategory(InputValidator.sanitizeInput(entry.getCategory()));

        try {
            String sql = """
                INSERT INTO passwords (user_id, title, username, password, url, notes, category)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, currentUserId);
                pstmt.setString(2, encryptor.encrypt(entry.getTitle()));
                pstmt.setString(3, encryptor.encrypt(entry.getUsername()));
                pstmt.setString(4, encryptor.encrypt(entry.getPassword()));
                pstmt.setString(5, encryptor.encrypt(entry.getUrl()));
                pstmt.setString(6, encryptor.encrypt(entry.getNotes()));
                pstmt.setString(7, encryptor.encrypt(entry.getCategory()));
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
                    entry.setTitle(encryptor.decrypt(rs.getString("title")));
                    
                    String encryptedUsername = rs.getString("username");
                    String encryptedPassword = rs.getString("password");
                    
                    entry.setUsername(encryptedUsername != null ? encryptor.decrypt(encryptedUsername) : "");
                    entry.setPassword(encryptedPassword != null ? encryptor.decrypt(encryptedPassword) : "");
                    entry.setUrl(rs.getString("url") != null ? encryptor.decrypt(rs.getString("url")) : "");
                    entry.setNotes(rs.getString("notes") != null ? encryptor.decrypt(rs.getString("notes")) : "");
                    entry.setCategory(rs.getString("category") != null ? encryptor.decrypt(rs.getString("category")) : "");
                    entry.setLastModified(rs.getLong("last_modified"));
                    passwords.add(entry);
                }
            }
        } catch (Exception e) {
            throw new SQLException("Error decrypting data", e);
        }
        return passwords;
    }

    public void updatePasswordEntry(PasswordEntry entry) throws SQLException, ValidationException {
        if (encryptor == null || currentUserId == -1) {
            throw new SQLException("Not logged in. Please log in first.");
        }

        InputValidator.validatePasswordEntry(
            entry.getTitle(),
            entry.getUsername(),
            entry.getPassword(),
            entry.getUrl(),
            entry.getNotes(),
            entry.getCategory()
        );

        entry.setTitle(InputValidator.sanitizeInput(entry.getTitle()));
        entry.setUsername(InputValidator.sanitizeInput(entry.getUsername()));
        entry.setPassword(InputValidator.sanitizeInput(entry.getPassword()));
        entry.setUrl(InputValidator.sanitizeInput(entry.getUrl()));
        entry.setNotes(InputValidator.sanitizeInput(entry.getNotes()));
        entry.setCategory(InputValidator.sanitizeInput(entry.getCategory()));

        try {
            String sql = """
                UPDATE passwords 
                SET title = ?, username = ?, password = ?, url = ?, notes = ?, 
                    category = ?, last_modified = ? 
                WHERE id = ? AND user_id = ?
            """;
            
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, encryptor.encrypt(entry.getTitle()));
                pstmt.setString(2, encryptor.encrypt(entry.getUsername()));
                pstmt.setString(3, encryptor.encrypt(entry.getPassword()));
                pstmt.setString(4, encryptor.encrypt(entry.getUrl()));
                pstmt.setString(5, encryptor.encrypt(entry.getNotes()));
                pstmt.setString(6, encryptor.encrypt(entry.getCategory()));
                pstmt.setLong(7, entry.getLastModified());
                pstmt.setInt(8, entry.getId());
                pstmt.setInt(9, currentUserId);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new SQLException("Error encrypting data", e);
        }
    }

    public void deletePasswordEntry(int entryId) throws SQLException {
        PasswordEntry entry = getPasswordEntry(entryId);
        if (entry != null) {
            entry.secureClear();
        }

        String sql = "DELETE FROM passwords WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, entryId);
            pstmt.setInt(2, currentUserId);
            pstmt.executeUpdate();
        }
    }

    private void migrateUnencryptedData() {
        try {
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
            stmt.execute("DELETE FROM passwords");
            stmt.execute("DELETE FROM users");
            System.out.println("User and all associated data deleted.");
        }
    }

    public void wipeDatabase() throws SQLException {
        String sql = "SELECT id FROM passwords";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                deletePasswordEntry(rs.getInt("id"));
            }
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM passwords");
            stmt.executeUpdate("DELETE FROM users");
            stmt.executeUpdate("DELETE FROM salt_history");
        }

        try {
            connection.close();
            File dbFile = new File(DB_NAME);
            SecureWiper.secureDeleteFile(dbFile);
            
            connection = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
            createTables();
        } catch (IOException e) {
            throw new SQLException("Failed to securely wipe database file", e);
        }
    }

    public void deleteCurrentUser() throws SQLException {
        List<PasswordEntry> entries = getAllPasswords();
        for (PasswordEntry entry : entries) {
            entry.secureClear();
        }

        String sql = "DELETE FROM passwords WHERE user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, currentUserId);
            pstmt.executeUpdate();
        }

        sql = "DELETE FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, currentUserId);
            pstmt.executeUpdate();
        }

        if (encryptor != null) {
            encryptor.secureWipeKeys();
        }
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
                String encryptedCategory = rs.getString("category");
                if (encryptedCategory != null && !encryptedCategory.isEmpty()) {
                    try {
                        String decryptedCategory = encryptor.decrypt(encryptedCategory);
                        if (decryptedCategory != null && !decryptedCategory.isEmpty()) {
                            categories.add(decryptedCategory);
                        }
                    } catch (Exception e) {
                        categories.add(encryptedCategory);
                    }
                }
            }
        }
        return categories;
    }

    private PasswordEntry getPasswordEntry(int entryId) throws SQLException {
        String sql = "SELECT title, username, password, url, notes, category FROM passwords WHERE id = ? AND user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, entryId);
            pstmt.setInt(2, currentUserId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    try {
                        PasswordEntry entry = new PasswordEntry();
                        entry.setId(entryId);
                        entry.setTitle(encryptor.decrypt(rs.getString("title")));
                        entry.setUsername(encryptor.decrypt(rs.getString("username")));
                        entry.setPassword(encryptor.decrypt(rs.getString("password")));
                        entry.setUrl(encryptor.decrypt(rs.getString("url")));
                        entry.setNotes(encryptor.decrypt(rs.getString("notes")));
                        entry.setCategory(encryptor.decrypt(rs.getString("category")));
                        return entry;
                    } catch (Exception e) {
                        throw new SQLException("Failed to decrypt password entry: " + e.getMessage(), e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gets the current master key (DEK) used for encryption
     * @return The current master key
     * @throws SQLException if not logged in
     */
    public SecretKey getMasterKey() throws SQLException {
        if (encryptor == null) {
            throw new SQLException("Not logged in. Please log in first.");
        }
        return encryptor.getDEK();
    }

    /**
     * Gets the path to the current database file
     * @return The database file path
     */
    public String getDatabasePath() {
        return new File(DB_NAME).getAbsolutePath();
    }
} 