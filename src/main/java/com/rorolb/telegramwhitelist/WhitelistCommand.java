package com.yourname.telegramwhitelist;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WhitelistCommand implements CommandExecutor, TabCompleter {
    private final TelegramWhitelist plugin;
    
    public WhitelistCommand(TelegramWhitelist plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("whitelist")) {
            // Подкоманды для интеграции с существующей системой вайтлиста
            if (args.length > 0 && args[0].equalsIgnoreCase("tg")) {
                return handleTGSubcommand(sender, args);
            }
            return false; // Пусть стандартная команда whitelist обрабатывается сервером
        }
        return false;
    }
    
    private boolean handleTGSubcommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§e§lTelegram Whitelist Commands:");
            sender.sendMessage("§6/whitelist tg list §7- Список ожидающих заявок");
            sender.sendMessage("§6/whitelist tg approve <ник> §7- Одобрить заявку");
            sender.sendMessage("§6/whitelist tg reject <ник> §7- Отклонить заявку");
            sender.sendMessage("§6/whitelist tg reload §7- Перезагрузить конфиг");
            sender.sendMessage("§6/whitelist tg status §7- Статус бота");
            return true;
        }
        
        String subcommand = args[1].toLowerCase();
        
        switch (subcommand) {
            case "list":
                return listPendingRequests(sender);
            case "approve":
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /whitelist tg approve <ник>");
                    return true;
                }
                return approveRequest(sender, args[2]);
            case "reject":
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /whitelist tg reject <ник>");
                    return true;
                }
                return rejectRequest(sender, args[2]);
            case "reload":
                return reloadConfig(sender);
            case "status":
                return showStatus(sender);
            default:
                sender.sendMessage("§cНеизвестная подкоманда: " + subcommand);
                return true;
        }
    }
    
    private boolean listPendingRequests(CommandSender sender) {
        if (!sender.hasPermission("tgwhitelist.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды!");
            return true;
        }
        
        var pendingRequests = plugin.getPendingRequests();
        
        if (pendingRequests.isEmpty()) {
            sender.sendMessage("§aНет ожидающих заявок.");
            return true;
        }
        
        sender.sendMessage("§e§lОжидающие заявки (§6" + pendingRequests.size() + "§e):");
        int i = 1;
        for (var entry : pendingRequests.entrySet()) {
            PendingRequest request = entry.getValue();
            sender.sendMessage("§6" + i + ". §f" + request.getUsername() + 
                             " §7(TG: @" + request.getTelegramUsername() + 
                             ", ID: " + request.getTelegramId() + ")");
            i++;
        }
        return true;
    }
    
    private boolean approveRequest(CommandSender sender, String username) {
        if (!sender.hasPermission("tgwhitelist.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды!");
            return true;
        }
        
        var pendingRequests = plugin.getPendingRequests();
        String requestId = null;
        
        // Ищем заявку по нику
        for (var entry : pendingRequests.entrySet()) {
            if (entry.getValue().getUsername().equalsIgnoreCase(username)) {
                requestId = entry.getKey();
                break;
            }
        }
        
        if (requestId == null) {
            sender.sendMessage("§cЗаявка от игрока " + username + " не найдена!");
            return true;
        }
        
        plugin.approveRequest(requestId);
        sender.sendMessage("§aЗаявка от " + username + " одобрена!");
        return true;
    }
    
    private boolean rejectRequest(CommandSender sender, String username) {
        if (!sender.hasPermission("tgwhitelist.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды!");
            return true;
        }
        
        var pendingRequests = plugin.getPendingRequests();
        String requestId = null;
        
        // Ищем заявку по нику
        for (var entry : pendingRequests.entrySet()) {
            if (entry.getValue().getUsername().equalsIgnoreCase(username)) {
                requestId = entry.getKey();
                break;
            }
        }
        
        if (requestId == null) {
            sender.sendMessage("§cЗаявка от игрока " + username + " не найдена!");
            return true;
        }
        
        plugin.rejectRequest(requestId);
        sender.sendMessage("§aЗаявка от " + username + " отклонена!");
        return true;
    }
    
    private boolean reloadConfig(CommandSender sender) {
        if (!sender.hasPermission("tgwhitelist.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды!");
            return true;
        }
        
        plugin.reloadConfig();
        sender.sendMessage("§aКонфигурация перезагружена!");
        return true;
    }
    
    private boolean showStatus(CommandSender sender) {
        if (!sender.hasPermission("tgwhitelist.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды!");
            return true;
        }
        
        var pendingRequests = plugin.getPendingRequests();
        
        sender.sendMessage("§e§lСтатус Telegram Whitelist:");
        sender.sendMessage("§6Ожидающие заявки: §f" + pendingRequests.size());
        sender.sendMessage("§6Бот активен: §aДа");
        sender.sendMessage("§6Версия плагина: §f1.0.0");
        sender.sendMessage("§6Поддержка Minecraft: §f1.21.4");
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("whitelist")) {
            return Collections.emptyList();
        }
        
        // /whitelist tg [subcommand]
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], 
                Arrays.asList("tg"), new ArrayList<>());
        }
        
        // /whitelist tg [subcommand]
        if (args.length == 2 && args[0].equalsIgnoreCase("tg")) {
            List<String> subcommands = Arrays.asList("list", "approve", "reject", "reload", "status");
            return StringUtil.copyPartialMatches(args[1], subcommands, new ArrayList<>());
        }
        
        // /whitelist tg approve/reject [username]
        if (args.length == 3 && args[0].equalsIgnoreCase("tg") && 
            (args[1].equalsIgnoreCase("approve") || args[1].equalsIgnoreCase("reject"))) {
            
            List<String> usernames = new ArrayList<>();
            var pendingRequests = plugin.getPendingRequests();
            
            for (var request : pendingRequests.values()) {
                usernames.add(request.getUsername());
            }
            
            return StringUtil.copyPartialMatches(args[2], usernames, new ArrayList<>());
        }
        
        return Collections.emptyList();
    }
}
