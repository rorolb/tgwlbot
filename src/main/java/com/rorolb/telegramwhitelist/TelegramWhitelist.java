package com.yourname.telegramwhitelist;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.*;
import java.util.logging.Logger;

public class TelegramWhitelist extends JavaPlugin implements Listener {
    private TelegramBot bot;
    private Logger logger;
    private Map<String, PendingRequest> pendingRequests;
    private ConfigManager configManager;
    
        @Override
    public void onEnable() {
        this.logger = getLogger();
        this.pendingRequests = new HashMap<>();
        this.configManager = new ConfigManager(this);
        
        saveDefaultConfig();
        configManager.loadConfig();
        
        // Инициализация бота
        String botToken = configManager.getBotToken();
        String chatId = configManager.getChatId();
        String adminChatId = configManager.getAdminChatId();
        
        if (botToken.isEmpty() || chatId.isEmpty()) {
            logger.warning("Токен бота или ID чата не настроены! Проверьте config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        this.bot = new TelegramBot(botToken, chatId, adminChatId, this);
        
        // Регистрация команд
        WhitelistCommand whitelistCommand = new WhitelistCommand(this);
        PluginCommand command = getCommand("whitelist");
        if (command != null) {
            command.setExecutor(whitelistCommand);
            command.setTabCompleter(whitelistCommand);
        }
        
        // Регистрация собственной команды
        getCommand("tgwhitelist").setExecutor(this);
        
        // Регистрация событий
        getServer().getPluginManager().registerEvents(this, this);
        
        logger.info("Плагин TelegramWhitelist успешно запущен!");
    }
    
    @Override
    public void onDisable() {
        if (bot != null) {
            bot.shutdown();
        }
        logger.info("Плагин TelegramWhitelist отключен!");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isWhitelisted()) {
            player.kickPlayer("Вы не в вайтлисте! Подайте заявку через Telegram бота.");
        }
    }
    
    public void addPendingRequest(String username, long telegramId, String telegramUsername) {
        String requestId = UUID.randomUUID().toString();
        PendingRequest request = new PendingRequest(username, telegramId, telegramUsername);
        pendingRequests.put(requestId, request);
        
        // Отправляем заявку в тему группы
        bot.sendRequestToTopic(requestId, username, telegramUsername, telegramId);
    }
    
    public void approveRequest(String requestId) {
        PendingRequest request = pendingRequests.get(requestId);
        if (request != null) {
            // Добавляем в вайтлист
            getServer().getWhitelistedPlayers().add(request.getUsername());
            getServer().reloadWhitelist();
            
            // Отправляем сообщение в Telegram
            bot.sendMessageToUser(request.getTelegramId(), 
                "✅ Ваша заявка на вайтлист принята! Теперь вы можете зайти на сервер.");
            
            // Логируем действие
            logger.info("Заявка от " + request.getUsername() + " одобрена.");
            
            pendingRequests.remove(requestId);
        }
    }
    
    public void rejectRequest(String requestId) {
        PendingRequest request = pendingRequests.get(requestId);
        if (request != null) {
            // Отправляем сообщение об отказе
            bot.sendMessageToUser(request.getTelegramId(), 
                "❌ Ваша заявка на вайтлист отклонена.");
            
            // Логируем действие
            logger.info("Заявка от " + request.getUsername() + " отклонена.");
            
            pendingRequests.remove(requestId);
        }
    }
    
    public Map<String, PendingRequest> getPendingRequests() {
        return pendingRequests;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("tgwhitelist")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload") && sender.hasPermission("tgwhitelist.admin")) {
                reloadConfig();
                configManager.loadConfig();
                sender.sendMessage("§aКонфигурация перезагружена!");
                return true;
            }
            sender.sendMessage("§eTelegramWhitelist v1.0");
            sender.sendMessage("§7Используйте: /tgwhitelist reload");
            return true;
        }
        return false;
    }

}
