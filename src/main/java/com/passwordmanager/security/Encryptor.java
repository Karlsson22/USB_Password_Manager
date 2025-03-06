package com.passwordmanager.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class Encryptor {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int ITERATION_COUNT = 65536;

    private volatile SecretKey dek;
    private final SecureRandom secureRandom;

    public Encryptor(SecretKey dek) {
        this.dek = dek;
        this.secureRandom = new SecureRandom();
    }

    public static SecretKey generateDEK() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(KEY_LENGTH);
        return keyGen.generateKey();
    }

    public static SecretKey deriveKEK(String masterPassword, String salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(
            masterPassword.toCharArray(),
            Base64.getDecoder().decode(salt),
            ITERATION_COUNT,
            KEY_LENGTH
        );
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    public static String encryptDEK(SecretKey dek, SecretKey kek) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, kek, parameterSpec);

        byte[] encryptedDEK = cipher.doFinal(dek.getEncoded());
        byte[] combined = new byte[iv.length + encryptedDEK.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedDEK, 0, combined, iv.length, encryptedDEK.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    public static SecretKey decryptDEK(String encryptedDEK, SecretKey kek) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedDEK);
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, iv.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, kek, parameterSpec);

        byte[] dekBytes = cipher.doFinal(
            combined,
            GCM_IV_LENGTH,
            combined.length - GCM_IV_LENGTH
        );

        return new SecretKeySpec(dekBytes, "AES");
    }

    public String encrypt(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, dek, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

        byte[] encrypted = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);

        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String ciphertext) throws Exception {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return "";
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            
            if (decoded.length < GCM_IV_LENGTH) {
                return ciphertext;
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, iv.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, dek, parameterSpec);

            byte[] plaintext = cipher.doFinal(
                decoded, 
                GCM_IV_LENGTH, 
                decoded.length - GCM_IV_LENGTH
            );

            return new String(plaintext);
        } catch (IllegalArgumentException e) {
            return ciphertext;
        }
    }

    public void secureWipeKeys() {
        if (dek != null) {
            SecureWiper.wipeKey(dek);
            dek = null;
        }
    }

    /**
     * Gets the Data Encryption Key (DEK)
     * @return The current DEK
     */
    public SecretKey getDEK() {
        return dek;
    }

    /**
     * Encrypts a byte array using the DEK
     * @param data The data to encrypt
     * @return The encrypted data
     * @throws Exception if encryption fails
     */
    public byte[] encryptBytes(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, dek, parameterSpec);

        byte[] ciphertext = cipher.doFinal(data);

        byte[] encrypted = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length);

        return encrypted;
    }

    /**
     * Decrypts a byte array using the DEK
     * @param data The data to decrypt
     * @return The decrypted data
     * @throws Exception if decryption fails
     */
    public byte[] decryptBytes(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        
        if (data.length < GCM_IV_LENGTH) {
            throw new Exception("Invalid encrypted data");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(data, 0, iv, 0, iv.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, dek, parameterSpec);

        return cipher.doFinal(
            data, 
            GCM_IV_LENGTH, 
            data.length - GCM_IV_LENGTH
        );
    }
} 