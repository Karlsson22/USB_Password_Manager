package com.passwordmanager.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
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

    private final SecretKey key;
    private final SecureRandom secureRandom;

    public Encryptor(String masterPassword, String salt) throws Exception {
        this.key = generateKey(masterPassword, salt);
        this.secureRandom = new SecureRandom();
    }

    private SecretKey generateKey(String password, String salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            Base64.getDecoder().decode(salt),
            ITERATION_COUNT,
            KEY_LENGTH
        );
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    public String encrypt(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

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
                // If the data is too short to be encrypted, return it as-is
                // This handles legacy unencrypted data
                return ciphertext;
            }

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, iv.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] plaintext = cipher.doFinal(
                decoded, 
                GCM_IV_LENGTH, 
                decoded.length - GCM_IV_LENGTH
            );

            return new String(plaintext);
        } catch (IllegalArgumentException e) {
            // If decoding fails, assume it's unencrypted data
            return ciphertext;
        }
    }
} 