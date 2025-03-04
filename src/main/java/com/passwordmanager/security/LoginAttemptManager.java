package com.passwordmanager.security;

import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.io.*;

public class LoginAttemptManager {
    private static final int MAX_ATTEMPTS = 5;
    private static final int INITIAL_LOCKOUT_SECONDS = 300; // 5 minutes
    private static final int MAX_LOCKOUT_SECONDS = 3600; // 1 hour
    private static final String LOCKOUT_FILE = "lockout_data.ser";
    
    private static final String GLOBAL_KEY = "GLOBAL"; // Single key for all attempts
    
    private AtomicInteger globalAttemptCount;
    private volatile Instant globalLockoutUntil;
    
    private static LoginAttemptManager instance;
    
    private LoginAttemptManager() {
        this.globalAttemptCount = new AtomicInteger(0);
        this.globalLockoutUntil = null;
        loadLockoutData();
    }
    
    public static synchronized LoginAttemptManager getInstance() {
        if (instance == null) {
            instance = new LoginAttemptManager();
        }
        return instance;
    }
    
    private void loadLockoutData() {
        File file = new File(LOCKOUT_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                Integer savedAttempts = (Integer) ois.readObject();
                Instant savedLockout = (Instant) ois.readObject();
                
                // Only restore lockout if it hasn't expired
                if (savedLockout != null && Instant.now().isBefore(savedLockout)) {
                    globalAttemptCount.set(savedAttempts);
                    globalLockoutUntil = savedLockout;
                }
            } catch (Exception e) {
                System.err.println("Error loading lockout data: " + e.getMessage());
            }
        }
    }
    
    private void saveLockoutData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(LOCKOUT_FILE))) {
            // Only save if there's an active lockout
            if (isLockedOut(GLOBAL_KEY)) {
                oos.writeObject(globalAttemptCount.get());
                oos.writeObject(globalLockoutUntil);
            } else {
                // Clear the file if no active lockout
                oos.writeObject(0);
                oos.writeObject(null);
            }
        } catch (Exception e) {
            System.err.println("Error saving lockout data: " + e.getMessage());
        }
    }
    
    public void recordFailedAttempt(String ignored) { // parameter ignored, using global counter
        int attempts = globalAttemptCount.incrementAndGet();
        
        if (attempts >= MAX_ATTEMPTS) {
            // Calculate lockout duration using exponential backoff
            // Start with 5 minutes, then 10, 20, 40 minutes up to 1 hour
            int lockoutDuration = Math.min(
                INITIAL_LOCKOUT_SECONDS * (1 << (attempts - MAX_ATTEMPTS)),
                MAX_LOCKOUT_SECONDS
            );
            
            globalLockoutUntil = Instant.now().plusSeconds(lockoutDuration);
            saveLockoutData();
        }
    }
    
    public boolean isLockedOut(String ignored) { // parameter ignored, using global lockout
        if (globalLockoutUntil != null) {
            if (Instant.now().isBefore(globalLockoutUntil)) {
                return true;
            } else {
                // Lockout period has expired, reset attempts
                resetAttempts(GLOBAL_KEY);
                return false;
            }
        }
        return false;
    }
    
    public long getRemainingLockoutSeconds(String ignored) { // parameter ignored, using global lockout
        if (globalLockoutUntil != null && Instant.now().isBefore(globalLockoutUntil)) {
            return Instant.now().until(globalLockoutUntil, java.time.temporal.ChronoUnit.SECONDS);
        }
        return 0;
    }
    
    public void resetAttempts(String ignored) { // parameter ignored, using global counter
        globalAttemptCount.set(0);
        globalLockoutUntil = null;
        saveLockoutData();
    }
    
    public int getRemainingAttempts(String ignored) { // parameter ignored, using global counter
        return Math.max(0, MAX_ATTEMPTS - globalAttemptCount.get());
    }
} 