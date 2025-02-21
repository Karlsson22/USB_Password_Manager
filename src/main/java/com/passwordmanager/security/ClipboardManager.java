package com.passwordmanager.security;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;

public class ClipboardManager {
    private static final int DEFAULT_CLEAR_DELAY = 30; // seconds
    private static Timer clearTimer;

    public static void copyToClipboard(String text, boolean isSensitive) {
        // Copy to clipboard
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);

        // If sensitive data, schedule clearing
        if (isSensitive) {
            scheduleClearClipboard();
        }
    }

    private static void scheduleClearClipboard() {
        // Cancel any existing timer
        if (clearTimer != null) {
            clearTimer.cancel();
        }

        clearTimer = new Timer(true); // Create daemon timer
        clearTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    clipboard.clear();
                });
            }
        }, DEFAULT_CLEAR_DELAY * 1000); // Convert seconds to milliseconds
    }
} 