package com.passwordmanager.model;

import javafx.beans.property.SimpleStringProperty;

public class PasswordEntry {
    private int id;
    private String title;
    private String username;
    private String password;
    private String url;
    private String notes;
    private String category;
    private long lastModified;

    
    public PasswordEntry() {}

    public PasswordEntry(String title, String username, String password, String url, String notes, String category) {
        this.title = title;
        this.username = username;
        this.password = password;
        this.url = url;
        this.notes = notes;
        this.category = category;
        this.lastModified = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public void updateLastModified() {
        this.lastModified = System.currentTimeMillis();
    }
} 