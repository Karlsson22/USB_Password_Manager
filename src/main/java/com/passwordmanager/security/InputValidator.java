package com.passwordmanager.security;

import java.util.regex.Pattern;

public class InputValidator {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._@-]{3,50}$");
    private static final Pattern TITLE_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s._@#$%&*()-]{1,100}$");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s._-]{1,50}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://)?[\\w.-]+\\.[\\w]{2,}[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]*$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^.+$"); // Any non-empty string
    private static final Pattern NOTES_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s\\p{P}]{0,1000}$");
    
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("'", "''")
                   .replace("\\", "\\\\")
                   .replace("\u0000", " ")
                   .replace("\n", " ")
                   .replace("\r", " ");
    }
    
    public static boolean isValidInput(String input) {
        return input != null && !input.trim().isEmpty();
    }
    
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username).matches();
    }
    
    public static boolean isValidTitle(String title) {
        return title != null && TITLE_PATTERN.matcher(title).matches();
    }
    
    public static boolean isValidCategory(String category) {
        return category == null || category.isEmpty() || CATEGORY_PATTERN.matcher(category).matches();
    }
    
    public static boolean isValidUrl(String url) {
        return url == null || url.isEmpty() || URL_PATTERN.matcher(url).matches();
    }
    
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }
    
    public static boolean isValidNotes(String notes) {
        return notes == null || notes.isEmpty() || NOTES_PATTERN.matcher(notes).matches();
    }
    
    public static boolean isValidMasterPassword(String masterPassword) {
        return masterPassword != null && !masterPassword.trim().isEmpty();
    }
    
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
    
    public static void validatePasswordEntry(String title, String username, String password, 
                                          String url, String notes, String category) 
            throws ValidationException {
        if (!isValidInput(title)) {
            throw new ValidationException("Title is required");
        }
        if (!isValidInput(username)) {
            throw new ValidationException("Username is required");
        }
        if (!isValidInput(password)) {
            throw new ValidationException("Password is required");
        }
    }
} 