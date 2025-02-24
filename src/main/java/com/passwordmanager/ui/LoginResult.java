package com.passwordmanager.ui;

public class LoginResult {
    private final String masterPassword;
    private final String keyFilePath;

    public LoginResult(String masterPassword, String keyFilePath) {
        this.masterPassword = masterPassword;
        this.keyFilePath = keyFilePath;
    }

    public String getMasterPassword() { return masterPassword; }
    public String getKeyFilePath() { return keyFilePath; }
} 