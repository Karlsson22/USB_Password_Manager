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
        // Generate a random key
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE, new SecureRandom());
        SecretKey key = keyGen.generateKey();
        
        // Generate additional random data
        byte[] additionalData = new byte[ADDITIONAL_BYTES];
        SecureRandom random = new SecureRandom();
        random.nextBytes(additionalData);
        
        // Generate a strong salt
        String salt = PasswordHasher.generateSalt();
        
        // Create an encryptor with increased iterations
        Encryptor encryptor = new Encryptor(masterPassword, salt, ITERATIONS);
        
        // Encrypt both the key and additional data
        String encryptedKey = encryptor.encrypt(Base64.getEncoder().encodeToString(key.getEncoded()));
        String encryptedData = encryptor.encrypt(Base64.getEncoder().encodeToString(additionalData));
        
        // Add some random positions for the real key
        int keyPosition = random.nextInt(ADDITIONAL_BYTES);
        
        // Save everything to file with a checksum
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            StringBuilder fileContent = new StringBuilder();
            fileContent.append(salt).append("\n");
            fileContent.append(keyPosition).append("\n");
            fileContent.append(encryptedKey).append("\n");
            fileContent.append(encryptedData).append("\n");
            
            // Add a verification hash
            String verificationHash = PasswordHasher.hashPassword(
                encryptedKey + encryptedData, 
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
            String encryptedKey = fileContent[2];
            String encryptedData = fileContent[3];
            String storedHash = fileContent[4];
            
            // Verify the file hasn't been tampered with
            String calculatedHash = PasswordHasher.hashPassword(
                encryptedKey + encryptedData, 
                salt
            );
            if (!calculatedHash.equals(storedHash)) {
                return false;
            }
            
            // Try to decrypt with increased iterations
            Encryptor encryptor = new Encryptor(masterPassword, salt, ITERATIONS);
            String decryptedKey = encryptor.decrypt(encryptedKey);
            String decryptedData = encryptor.decrypt(encryptedData);
            
            // Verify both parts can be decoded as Base64
            Base64.getDecoder().decode(decryptedKey);
            Base64.getDecoder().decode(decryptedData);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 