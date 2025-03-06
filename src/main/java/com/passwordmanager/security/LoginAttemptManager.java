package com.passwordmanager.security;

import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.io.*;

public class LoginAttemptManager {
    private static final int MAX_ATTEMPTS = 5;
    private static final int INITIAL_LOCKOUT_SECONDS = 300;
    private static final int MAX_LOCKOUT_SECONDS = 3600;
    private static final String LOCKOUT_FILE = "lockout_data.ser";
    
    private static final String GLOBAL_KEY = "GLOBAL";
    
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
            if (isLockedOut(GLOBAL_KEY)) {
                oos.writeObject(globalAttemptCount.get());
                oos.writeObject(globalLockoutUntil);
            } else {
                oos.writeObject(0);
                oos.writeObject(null);
            }
        } catch (Exception e) {
            System.err.println("Error saving lockout data: " + e.getMessage());
        }
    }
    
    public void recordFailedAttempt(String ignored) {
        int attempts = globalAttemptCount.incrementAndGet();
        
        if (attempts >= MAX_ATTEMPTS) {
            int lockoutDuration = Math.min(
                INITIAL_LOCKOUT_SECONDS * (1 << (attempts - MAX_ATTEMPTS)),
                MAX_LOCKOUT_SECONDS
            );
            
            globalLockoutUntil = Instant.now().plusSeconds(lockoutDuration);
            saveLockoutData();
        }
    }
    
    public boolean isLockedOut(String ignored) {
        if (globalLockoutUntil != null) {
            if (Instant.now().isBefore(globalLockoutUntil)) {
                return true;
            } else {
                resetAttempts(GLOBAL_KEY);
                return false;
            }
        }
        return false;
    }
    
    public long getRemainingLockoutSeconds(String ignored) {
        if (globalLockoutUntil != null && Instant.now().isBefore(globalLockoutUntil)) {
            return Instant.now().until(globalLockoutUntil, java.time.temporal.ChronoUnit.SECONDS);
        }
        return 0;
    }
    
    public void resetAttempts(String ignored) {
        globalAttemptCount.set(0);
        globalLockoutUntil = null;
        saveLockoutData();
    }
    
    public int getRemainingAttempts(String ignored) {
        return Math.max(0, MAX_ATTEMPTS - globalAttemptCount.get());
    }
} 