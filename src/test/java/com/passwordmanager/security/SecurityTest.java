package com.passwordmanager.security;

import com.passwordmanager.database.DatabaseManager;
import com.passwordmanager.model.PasswordEntry;
import org.junit.jupiter.api.*;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

public class SecurityTest {
    private DatabaseManager dbManager;
    private static final String TEST_MASTER_PASSWORD = "TestPassword123!";
    private static final String NEW_MASTER_PASSWORD = "NewPassword456!";

    @BeforeEach
    void setUp() {
        // Delete the test database file if it exists
        java.io.File dbFile = new java.io.File("passwords.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
        dbManager = new DatabaseManager();
    }

    @AfterEach
    void tearDown() {
        if (dbManager != null) {
            dbManager.closeConnection();
        }
        // Clean up the test database
        java.io.File dbFile = new java.io.File("passwords.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    void testEnvelopeEncryption() throws SQLException {
        // Test user creation with envelope encryption
        assertTrue(dbManager.createUser(TEST_MASTER_PASSWORD), "User creation should succeed");
        
        // Verify that the master password works
        assertTrue(dbManager.verifyMasterPassword(TEST_MASTER_PASSWORD), "Master password verification should succeed");
        
        // Initialize the database with the master password
        dbManager.initializeDatabase(TEST_MASTER_PASSWORD);
        
        // Add an encrypted password entry
        PasswordEntry entry = new PasswordEntry();
        entry.setTitle("Test Account");
        entry.setUsername("testuser");
        entry.setPassword("secretpassword");
        entry.setUrl("https://test.com");
        entry.setNotes("Test notes");
        entry.setCategory("Test");
        
        dbManager.addPasswordEntry(entry);
        
        // Retrieve and verify the encrypted entry
        var entries = dbManager.getAllPasswords();
        assertFalse(entries.isEmpty(), "Should have at least one password entry");
        
        PasswordEntry retrieved = entries.get(0);
        assertEquals("Test Account", retrieved.getTitle(), "Decrypted title should match");
        assertEquals("testuser", retrieved.getUsername(), "Decrypted username should match");
        assertEquals("secretpassword", retrieved.getPassword(), "Decrypted password should match");
    }

    @Test
    void testSaltRotation() throws SQLException {
        // Create a user
        assertTrue(dbManager.createUser(TEST_MASTER_PASSWORD), "User creation should succeed");
        
        // Initialize database first
        dbManager.initializeDatabase(TEST_MASTER_PASSWORD);
        
        // Get initial salt
        String initialSalt = getCurrentSalt();
        assertNotNull(initialSalt, "Initial salt should not be null");
        
        // Force salt rotation by directly updating the salt creation timestamp
        forceOldSaltTimestamp();
        
        // Re-initialize database to trigger salt rotation
        dbManager.initializeDatabase(TEST_MASTER_PASSWORD);
        
        // Get new salt
        String newSalt = getCurrentSalt();
        assertNotNull(newSalt, "New salt should not be null");
        assertNotEquals(initialSalt, newSalt, "Salt should have been rotated");
        
        // Verify old salt is in history
        assertTrue(isSaltInHistory(initialSalt), "Old salt should be in history");
        
        // Verify authentication still works
        assertTrue(dbManager.verifyMasterPassword(TEST_MASTER_PASSWORD), 
            "Authentication should still work after salt rotation");
    }

    private String getCurrentSalt() throws SQLException {
        try (PreparedStatement stmt = dbManager.getConnection()
                .prepareStatement("SELECT current_salt FROM users LIMIT 1")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("current_salt");
            }
            return null;
        }
    }

    private void forceOldSaltTimestamp() throws SQLException {
        try (PreparedStatement stmt = dbManager.getConnection()
                .prepareStatement(
                    "UPDATE salt_history SET created_at = datetime('now', '-31 days') WHERE retired_at IS NULL")) {
            stmt.executeUpdate();
        }
    }

    private boolean isSaltInHistory(String salt) throws SQLException {
        try (PreparedStatement stmt = dbManager.getConnection()
                .prepareStatement(
                    "SELECT COUNT(*) FROM salt_history WHERE salt = ?")) {
            stmt.setString(1, salt);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }
} 