package com.passwordmanager.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class InputValidatorTest {
    
    @Test
    void testValidMasterPassword() {
        assertTrue(InputValidator.isValidMasterPassword("simple"));
        assertTrue(InputValidator.isValidMasterPassword("123"));
        assertTrue(InputValidator.isValidMasterPassword("!@#"));
        assertTrue(InputValidator.isValidMasterPassword("any password is fine"));
        
        assertFalse(InputValidator.isValidMasterPassword(null));
        assertFalse(InputValidator.isValidMasterPassword(""));
        assertFalse(InputValidator.isValidMasterPassword("   ")); 
    }

    @Test
    void testValidUsername() {
        assertTrue(InputValidator.isValidUsername("john.doe"));
        assertTrue(InputValidator.isValidUsername("user123"));
        assertTrue(InputValidator.isValidUsername("user@domain"));
        assertFalse(InputValidator.isValidUsername("ab")); 
        assertFalse(InputValidator.isValidUsername("invalid username")); 
        assertFalse(InputValidator.isValidUsername("user$name")); 
    }

    @Test
    void testValidTitle() {
        assertTrue(InputValidator.isValidTitle("My Bank Account"));
        assertTrue(InputValidator.isValidTitle("Gmail - Personal"));
        assertTrue(InputValidator.isValidTitle("Work VPN #2"));
        assertFalse(InputValidator.isValidTitle("")); 
        assertFalse(InputValidator.isValidTitle("a".repeat(101))); 
    }

    @Test
    void testValidUrl() {
        assertTrue(InputValidator.isValidUrl("https://www.example.com"));
        assertTrue(InputValidator.isValidUrl("http://sub.domain.com/path?param=value"));
        assertTrue(InputValidator.isValidUrl("")); 
        assertTrue(InputValidator.isValidUrl(null)); 
        assertFalse(InputValidator.isValidUrl("not-a-url"));
        assertFalse(InputValidator.isValidUrl("ftp://invalid-scheme.com"));
    }

    @Test
    void testValidCategory() {
        assertTrue(InputValidator.isValidCategory("Banking"));
        assertTrue(InputValidator.isValidCategory("Work-Related"));
        assertTrue(InputValidator.isValidCategory("")); 
        assertTrue(InputValidator.isValidCategory(null)); 
        assertFalse(InputValidator.isValidCategory("Invalid@Category"));
        assertFalse(InputValidator.isValidCategory("a".repeat(51))); 
    }

    @Test
    void testValidNotes() {
        assertTrue(InputValidator.isValidNotes("Some notes about this account."));
        assertTrue(InputValidator.isValidNotes("")); 
        assertTrue(InputValidator.isValidNotes(null)); 
        assertFalse(InputValidator.isValidNotes("a".repeat(1001))); 
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
            "", 
            "valid.username",
            "ValidPass123!",
            "https://example.com",
            "Valid notes",
            "Valid-Category"
        ));
    }
} 