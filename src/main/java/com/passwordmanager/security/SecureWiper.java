package com.passwordmanager.security;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SecureWiper {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int WIPE_ITERATIONS = 3;
    
    /**
     * Securely wipes a char array by overwriting it with random data multiple times
     * before filling it with zeros.
     *
     * @param chars The char array to wipe
     */
    public static void wipeCharArray(char[] chars) {
        if (chars == null) return;
        
        for (int i = 0; i < WIPE_ITERATIONS; i++) {
            for (int j = 0; j < chars.length; j++) {
                chars[j] = (char) SECURE_RANDOM.nextInt(Character.MAX_VALUE + 1);
            }
        }
        Arrays.fill(chars, '\u0000');
    }
    
    /**
     * Securely wipes a byte array by overwriting it with random data multiple times
     * before filling it with zeros.
     *
     * @param bytes The byte array to wipe
     */
    public static void wipeByteArray(byte[] bytes) {
        if (bytes == null) return;
        
        for (int i = 0; i < WIPE_ITERATIONS; i++) {
            // Overwrite with random data
            SECURE_RANDOM.nextBytes(bytes);
        }
        // Final overwrite with zeros
        Arrays.fill(bytes, (byte) 0);
    }
    
    /**
     * Securely wipes a String by first getting its internal char array and then wiping it.
     * Note: Due to String immutability, this only helps if called before the String
     * is garbage collected.
     *
     * @param string The string to wipe
     * @return An empty string
     */
    public static String wipeString(String string) {
        if (string == null) return null;
        
        char[] chars = string.toCharArray();
        wipeCharArray(chars);
        
        return "";
    }
    
    /**
     * Securely wipes a SecretKey by overwriting its encoded form before nulling the reference.
     *
     * @param key The SecretKey to wipe
     */
    public static void wipeKey(SecretKey key) {
        if (key == null) return;
        
        if (key instanceof SecretKeySpec) {
            try {
                byte[] encoded = key.getEncoded();
                if (encoded != null) {
                    wipeByteArray(encoded);
                }
            } catch (Exception e) {
                System.err.println("Error wiping key: " + e.getMessage());
            }
        }
    }
    
    /**
     * Securely wipes a ByteBuffer by overwriting its contents multiple times.
     *
     * @param buffer The ByteBuffer to wipe
     */
    public static void wipeBuffer(ByteBuffer buffer) {
        if (buffer == null) return;
        
        try {
            buffer.clear();
            int size = buffer.capacity();
            
            for (int i = 0; i < WIPE_ITERATIONS; i++) {
                buffer.clear();
                byte[] randomData = new byte[size];
                SECURE_RANDOM.nextBytes(randomData);
                buffer.put(randomData);
            }
            
            buffer.clear();
            buffer.put(new byte[size]);
            
        } catch (Exception e) {
            System.err.println("Error wiping buffer: " + e.getMessage());
        }
    }
    
    /**
     * Securely deletes a file by overwriting its contents multiple times before deletion.
     *
     * @param file The file to securely delete
     * @throws IOException If there's an error during the secure deletion process
     */
    public static void secureDeleteFile(File file) throws IOException {
        if (!file.exists()) return;
        
        long length = file.length();
        
        try (FileOutputStream fos = new FileOutputStream(file);
             FileChannel channel = fos.getChannel()) {
            
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            
            for (int i = 0; i < WIPE_ITERATIONS; i++) {
                long position = 0;
                while (position < length) {
                    buffer.clear();
                    SECURE_RANDOM.nextBytes(buffer.array());
                    
                    while (buffer.hasRemaining() && position < length) {
                        position += channel.write(buffer);
                    }
                }
                channel.force(true);
            }
            
            buffer.clear();
            Arrays.fill(buffer.array(), (byte) 0);
            long position = 0;
            while (position < length) {
                buffer.clear();
                position += channel.write(buffer);
            }
            
            channel.force(true);
        }
        
        if (!file.delete()) {
            throw new IOException("Failed to delete file after secure wipe: " + file.getPath());
        }
    }
    
    /**
     * Securely wipes a temporary directory and all its contents.
     *
     * @param directory The directory to wipe
     * @throws IOException If there's an error during the secure deletion process
     */
    public static void secureTempCleanup(File directory) throws IOException {
        if (!directory.exists()) return;
        
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    secureDeleteFile(file);
                } else if (file.isDirectory()) {
                    secureTempCleanup(file);
                }
            }
        }
        
        if (!directory.delete()) {
            throw new IOException("Failed to delete directory: " + directory.getPath());
        }
    }
} 