package com.passwordmanager.security;

import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;

public class SaltManager {
    private static final int SALT_LENGTH = 32; // 256 bits
    private static final long SALT_ROTATION_PERIOD = 30 * 24 * 60 * 60 * 1000L; // 30 days in milliseconds
    private final Connection connection;
    private final int userId;
    private final SecureRandom secureRandom;
    private final String masterPassword;

    public SaltManager(Connection connection, int userId, String masterPassword) {
        this.connection = connection;
        this.userId = userId;
        this.masterPassword = masterPassword;
        this.secureRandom = new SecureRandom();
    }

    public String generateNewSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public void rotateSaltIfNeeded() throws SQLException {
        String currentSalt = getCurrentSalt();
        if (shouldRotateSalt()) {
            String newSalt = generateNewSalt();
            updateSalt(currentSalt, newSalt);
        }
    }

    private boolean shouldRotateSalt() throws SQLException {
        String sql = "SELECT created_at FROM salt_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                long lastRotation = rs.getTimestamp("created_at").getTime();
                return System.currentTimeMillis() - lastRotation >= SALT_ROTATION_PERIOD;
            }
        }
        return true; // No salt history found, should rotate
    }

    private String getCurrentSalt() throws SQLException {
        String sql = "SELECT current_salt FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("current_salt");
            }
            throw new SQLException("User not found");
        }
    }

    private void updateSalt(String oldSalt, String newSalt) throws SQLException {
        connection.setAutoCommit(false);
        try {
            // Get the current encrypted DEK
            String sql = "SELECT encrypted_dek FROM users WHERE id = ?";
            String encryptedDEK;
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("User not found");
                }
                encryptedDEK = rs.getString("encrypted_dek");
            }

            // Decrypt the DEK using the old salt
            SecretKey oldKEK = Encryptor.deriveKEK(masterPassword, oldSalt);
            SecretKey dek = Encryptor.decryptDEK(encryptedDEK, oldKEK);

            // Re-encrypt the DEK using the new salt
            SecretKey newKEK = Encryptor.deriveKEK(masterPassword, newSalt);
            String newEncryptedDEK = Encryptor.encryptDEK(dek, newKEK);

            // Add old salt to history
            String historySql = """
                INSERT INTO salt_history (user_id, salt, created_at, retired_at)
                VALUES (?, ?, (SELECT created_at FROM users WHERE id = ?), CURRENT_TIMESTAMP)
            """;
            try (PreparedStatement pstmt = connection.prepareStatement(historySql)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, oldSalt);
                pstmt.setInt(3, userId);
                pstmt.executeUpdate();
            }

            // Calculate new password hash with new salt
            String newPasswordHash = PasswordHasher.hashPassword(masterPassword, newSalt);

            // Update current salt, password hash, and encrypted DEK
            String updateSql = "UPDATE users SET current_salt = ?, master_password_hash = ?, encrypted_dek = ? WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(updateSql)) {
                pstmt.setString(1, newSalt);
                pstmt.setString(2, newPasswordHash);
                pstmt.setString(3, newEncryptedDEK);
                pstmt.setInt(4, userId);
                pstmt.executeUpdate();
            }

            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw new SQLException("Failed to update salt: " + e.getMessage(), e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public List<String> getActiveSalts() throws SQLException {
        List<String> salts = new ArrayList<>();
        
        // Get current salt
        String currentSalt = getCurrentSalt();
        salts.add(currentSalt);
        
        // Get recent historical salts (within transition period)
        String sql = """
            SELECT salt FROM salt_history 
            WHERE user_id = ? AND retired_at >= ? 
            ORDER BY created_at DESC
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setTimestamp(2, Timestamp.from(
                Instant.now().minusMillis(SALT_ROTATION_PERIOD)
            ));
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                salts.add(rs.getString("salt"));
            }
        }
        
        return salts;
    }
} 