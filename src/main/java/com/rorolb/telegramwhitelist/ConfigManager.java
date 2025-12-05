package com.yourname.telegramwhitelist;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

public class ConfigManager {
    private final TelegramWhitelist plugin;
    private String botToken;
    private String chatId;
    private String adminChatId;
    private List<String> admins;
    
    public ConfigManager(TelegramWhitelist plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        
        botToken = config.getString("telegram.bot-token", "");
        chatId = config.getString("telegram.chat-id", "");
        adminChatId = config.getString("telegram.admin-chat-id", "");
        admins = config.getStringList("telegram.admins");
        
        if (botToken.isEmpty() || chatId.isEmpty()) {
            plugin.getLogger().warning("Необходимо настроить config.yml!");
        }
    }
    
    public String getBotToken() {
        return botToken;
    }
    
    public String getChatId() {
        return chatId;
    }
    
    public String getAdminChatId() {
        return adminChatId;
    }
    
    public List<String> getAdmins() {
        return admins;
    }
}