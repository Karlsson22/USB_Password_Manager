package com.passwordmanager.ui;

public class SignUpResult {
    private final String masterPassword;
    private final String keyFilePath;

    public SignUpResult(String masterPassword, String keyFilePath) {
        this.masterPassword = masterPassword;
        this.keyFilePath = keyFilePath;
    }

    public String getMasterPassword() { return masterPassword; }
    public String getKeyFilePath() { return keyFilePath; }
} 