package com.passwordmanager.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InputValidatorTest {
    
    @Test
    void testValidMasterPassword() {
        // Test valid passwords (any non-empty string)
        assertTrue(InputValidator.isValidMasterPassword("simple"));
        assertTrue(InputValidator.isValidMasterPassword("123"));
        assertTrue(InputValidator.isValidMasterPassword("!@#"));
        assertTrue(InputValidator.isValidMasterPassword("any password is fine"));
        
        // Test invalid cases
        assertFalse(InputValidator.isValidMasterPassword(null));
        assertFalse(InputValidator.isValidMasterPassword(""));
        assertFalse(InputValidator.isValidMasterPassword("   ")); // Only whitespace
    }

    @Test
    void testValidUsername() {
        assertTrue(InputValidator.isValidUsername("john.doe"));
        assertTrue(InputValidator.isValidUsername("user123"));
        assertTrue(InputValidator.isValidUsername("user@domain"));
        assertFalse(InputValidator.isValidUsername("ab")); // Too short
        assertFalse(InputValidator.isValidUsername("invalid username")); // Contains space
        assertFalse(InputValidator.isValidUsername("user$name")); // Invalid character
    }

    @Test
    void testValidTitle() {
        assertTrue(InputValidator.isValidTitle("My Bank Account"));
        assertTrue(InputValidator.isValidTitle("Gmail - Personal"));
        assertTrue(InputValidator.isValidTitle("Work VPN #2"));
        assertFalse(InputValidator.isValidTitle("")); // Empty
        assertFalse(InputValidator.isValidTitle("a".repeat(101))); // Too long
    }

    @Test
    void testValidUrl() {
        assertTrue(InputValidator.isValidUrl("https://www.example.com"));
        assertTrue(InputValidator.isValidUrl("http://sub.domain.com/path?param=value"));
        assertTrue(InputValidator.isValidUrl("")); // Empty URL is valid
        assertTrue(InputValidator.isValidUrl(null)); // Null URL is valid
        assertFalse(InputValidator.isValidUrl("not-a-url"));
        assertFalse(InputValidator.isValidUrl("ftp://invalid-scheme.com"));
    }

    @Test
    void testValidCategory() {
        assertTrue(InputValidator.isValidCategory("Banking"));
        assertTrue(InputValidator.isValidCategory("Work-Related"));
        assertTrue(InputValidator.isValidCategory("")); // Empty category is valid
        assertTrue(InputValidator.isValidCategory(null)); // Null category is valid
        assertFalse(InputValidator.isValidCategory("Invalid@Category"));
        assertFalse(InputValidator.isValidCategory("a".repeat(51))); // Too long
    }

    @Test
    void testValidNotes() {
        assertTrue(InputValidator.isValidNotes("Some notes about this account."));
        assertTrue(InputValidator.isValidNotes("")); // Empty notes are valid
        assertTrue(InputValidator.isValidNotes(null)); // Null notes are valid
        assertFalse(InputValidator.isValidNotes("a".repeat(1001))); // Too long
    }

    @Test
    void testSanitizeInput() {
        assertEquals("Hello World", InputValidator.sanitizeInput("Hello\nWorld"));
        assertEquals("Safe Input", InputValidator.sanitizeInput("Safe\u0000Input"));
        assertNull(InputValidator.sanitizeInput(null));
        assertEquals("", InputValidator.sanitizeInput(""));
    }

    @Test
    void testValidatePasswordEntry() {
        assertDoesNotThrow(() -> InputValidator.validatePasswordEntry(
            "Valid Title",
            "valid.username",
            "ValidPass123!",
            "https://example.com",
            "Valid notes",
            "Valid-Category"
        ));

        assertThrows(InputValidator.ValidationException.class, () -> InputValidator.validatePasswordEntry(
            "", // Invalid title
            "valid.username",
            "ValidPass123!",
            "https://example.com",
            "Valid notes",
            "Valid-Category"
        ));
    }
} 