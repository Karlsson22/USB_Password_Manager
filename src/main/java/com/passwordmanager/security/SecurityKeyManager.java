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
    private static final int ADDITIONAL_BYTES = 1024; 
    private static final int ITERATIONS = 100000; 
    
    public static void generateKeyFile(String masterPassword, String filePath) throws Exception {
        SecretKey dek = Encryptor.generateDEK();
        
        byte[] additionalData = new byte[ADDITIONAL_BYTES];
        SecureRandom random = new SecureRandom();
        random.nextBytes(additionalData);
        
        String salt = PasswordHasher.generateSalt();
        
        SecretKey kek = Encryptor.deriveKEK(masterPassword, salt);
        
        Encryptor encryptor = new Encryptor(dek);
        
        String encryptedData = encryptor.encrypt(Base64.getEncoder().encodeToString(additionalData));
        String encryptedDEK = Encryptor.encryptDEK(dek, kek);
        
        int keyPosition = random.nextInt(ADDITIONAL_BYTES);
        
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            StringBuilder fileContent = new StringBuilder();
            fileContent.append(salt).append("\n");
            fileContent.append(keyPosition).append("\n");
            fileContent.append(encryptedDEK).append("\n");
            fileContent.append(encryptedData).append("\n");
            
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
            String[] fileContent = new String(Files.readAllBytes(Paths.get(filePath))).split("\n");
            if (fileContent.length != 5) {
                return false;
            }
            
            String salt = fileContent[0];
            String keyPosition = fileContent[1];
            String encryptedDEK = fileContent[2];
            String encryptedData = fileContent[3];
            String storedHash = fileContent[4];
            
            String calculatedHash = PasswordHasher.hashPassword(
                encryptedDEK + encryptedData, 
                salt
            );
            if (!calculatedHash.equals(storedHash)) {
                return false;
            }
            
            SecretKey kek = Encryptor.deriveKEK(masterPassword, salt);
            SecretKey dek = Encryptor.decryptDEK(encryptedDEK, kek);
            
            Encryptor encryptor = new Encryptor(dek);
            
            String decryptedData = encryptor.decrypt(encryptedData);
            
            Base64.getDecoder().decode(decryptedData);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 