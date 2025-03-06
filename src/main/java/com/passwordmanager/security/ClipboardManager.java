package com.passwordmanager.security;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;

public class ClipboardManager {
    private static final int DEFAULT_CLEAR_DELAY = 30;
    private static Timer clearTimer;

    public static void copyToClipboard(String text, boolean isSensitive) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);

        if (isSensitive) {
            scheduleClearClipboard();
        }
    }

    private static void scheduleClearClipboard() {
        if (clearTimer != null) {
            clearTimer.cancel();
        }

        clearTimer = new Timer(true);
        clearTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    clipboard.clear();
                });
            }
        }, DEFAULT_CLEAR_DELAY * 1000);     
    }
} 