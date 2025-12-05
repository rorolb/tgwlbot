package com.yourname.telegramwhitelist;

public class PendingRequest {
    private String username;
    private long telegramId;
    private String telegramUsername;
    
    public PendingRequest(String username, long telegramId, String telegramUsername) {
        this.username = username;
        this.telegramId = telegramId;
        this.telegramUsername = telegramUsername != null ? telegramUsername : "Нет username";
    }
    
    public String getUsername() {
        return username;
    }
    
    public long getTelegramId() {
        return telegramId;
    }
    
    public String getTelegramUsername() {
        return telegramUsername;
    }
}