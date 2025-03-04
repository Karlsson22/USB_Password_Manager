package com.passwordmanager.security;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SecurityKeyManager {
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private static final int ADDITIONAL_BYTES = 1024; // Add 1KB of random data
    private static final int ITERATIONS = 100000; // Increase PBKDF2 iterations
    
    public static void generateKeyFile(String masterPassword, String filePath) throws Exception {
        // Generate a random DEK
        SecretKey dek = Encryptor.generateDEK();
        
        // Generate additional random data
        byte[] additionalData = new byte[ADDITIONAL_BYTES];
        SecureRandom random = new SecureRandom();
        random.nextBytes(additionalData);
        
        // Generate a strong salt
        String salt = PasswordHasher.generateSalt();
        
        // Derive KEK from master password and salt
        SecretKey kek = Encryptor.deriveKEK(masterPassword, salt);
        
        // Create an encryptor with the DEK
        Encryptor encryptor = new Encryptor(dek);
        
        // Encrypt both the additional data and the DEK
        String encryptedData = encryptor.encrypt(Base64.getEncoder().encodeToString(additionalData));
        String encryptedDEK = Encryptor.encryptDEK(dek, kek);
        
        // Add some random positions for the real key
        int keyPosition = random.nextInt(ADDITIONAL_BYTES);
        
        // Save everything to file with a checksum
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            StringBuilder fileContent = new StringBuilder();
            fileContent.append(salt).append("\n");
            fileContent.append(keyPosition).append("\n");
            fileContent.append(encryptedDEK).append("\n");
            fileContent.append(encryptedData).append("\n");
            
            // Add a verification hash
            String verificationHash = PasswordHasher.hashPassword(
                encryptedDEK + encryptedData, 
                salt
            );
            fileContent.append(verificationHash);
            
            fos.write(fileContent.toString().getBytes());
        }
    }
    
    public static boolean verifyKeyFile(String masterPassword, String filePath) throws Exception {
        if (!Files.exists(Paths.get(filePath))) {
            return false;
        }
        
        try {
            // Read the key file
            String[] fileContent = new String(Files.readAllBytes(Paths.get(filePath))).split("\n");
            if (fileContent.length != 5) { // salt, position, key, data, hash
                return false;
            }
            
            String salt = fileContent[0];
            String keyPosition = fileContent[1];
            String encryptedDEK = fileContent[2];
            String encryptedData = fileContent[3];
            String storedHash = fileContent[4];
            
            // Verify the file hasn't been tampered with
            String calculatedHash = PasswordHasher.hashPassword(
                encryptedDEK + encryptedData, 
                salt
            );
            if (!calculatedHash.equals(storedHash)) {
                return false;
            }
            
            // Try to decrypt the DEK
            SecretKey kek = Encryptor.deriveKEK(masterPassword, salt);
            SecretKey dek = Encryptor.decryptDEK(encryptedDEK, kek);
            
            // Create an encryptor with the decrypted DEK
            Encryptor encryptor = new Encryptor(dek);
            
            // Try to decrypt the additional data
            String decryptedData = encryptor.decrypt(encryptedData);
            
            // Verify the decrypted data can be decoded as Base64
            Base64.getDecoder().decode(decryptedData);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 