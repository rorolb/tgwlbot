package com.yourname.telegramwhitelist;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelegramBot {
    private final String botToken;
    private final String chatId;
    private final String adminChatId;
    private final TelegramWhitelist plugin;
    private final OkHttpClient client;
    private ScheduledExecutorService scheduler;
    private long lastUpdateId = 0;
    private Map<String, String> userStates; // Состояния пользователей
    
    public TelegramBot(String botToken, String chatId, String adminChatId, TelegramWhitelist plugin) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.adminChatId = adminChatId;
        this.plugin = plugin;
        this.client = new OkHttpClient();
        this.userStates = new HashMap<>();
        
        startPolling();
    }
    
    private void startPolling() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::checkUpdates, 0, 2, TimeUnit.SECONDS);
    }
    
    private void checkUpdates() {
        try {
            String url = String.format("https://api.telegram.org/bot%s/getUpdates?offset=%d&timeout=10", 
                botToken, lastUpdateId + 1);
            
            Request request = new Request.Builder()
                .url(url)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return;
                
                JSONObject json = new JSONObject(response.body().string());
                if (!json.getBoolean("ok")) return;
                
                JSONArray updates = json.getJSONArray("result");
                
                for (int i = 0; i < updates.length(); i++) {
                    JSONObject update = updates.getJSONObject(i);
                    lastUpdateId = update.getLong("update_id");
                    
                    if (update.has("callback_query")) {
                        handleCallbackQuery(update.getJSONObject("callback_query"));
                    } else if (update.has("message")) {
                        handleMessage(update.getJSONObject("message"));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при опросе Telegram: " + e.getMessage());
        }
    }
    
    private void handleMessage(JSONObject message) {
        if (!message.has("text")) return;
        
        String text = message.getString("text");
        JSONObject from = message.getJSONObject("from");
        long userId = from.getLong("id");
        String username = from.has("username") ? from.getString("username") : null;
        
        // Если сообщение из группы/темы, пропускаем
        if (message.has("chat") && message.getJSONObject("chat").getLong("id") != userId) {
            return;
        }
        
        // Обработка команд
        if (text.equalsIgnoreCase("/start")) {
            sendMessage(userId, "Добро пожаловать! Для подачи заявки на вайтлист введите ваш никнейм Minecraft.");
            userStates.put(String.valueOf(userId), "WAITING_FOR_NICKNAME");
            return;
        }
        
        // Проверяем состояние пользователя
        String state = userStates.get(String.valueOf(userId));
        if (state != null && state.equals("WAITING_FOR_NICKNAME")) {
            // Валидация ника
            if (text.length() < 3 || text.length() > 16) {
                sendMessage(userId, "❌ Никнейм должен быть от 3 до 16 символов. Попробуйте снова.");
                return;
            }
            
            if (!text.matches("[a-zA-Z0-9_]+")) {
                sendMessage(userId, "❌ Никнейм может содержать только латинские буквы, цифры и подчеркивание.");
                return;
            }
            
            // Отправляем заявку
            plugin.addPendingRequest(text, userId, username);
            sendMessage(userId, "⏳ Ваша заявка отправлена на рассмотрение. Ожидайте решения.");
            userStates.remove(String.valueOf(userId));
        }
    }
    
    private void handleCallbackQuery(JSONObject callbackQuery) {
        JSONObject from = callbackQuery.getJSONObject("from");
        String data = callbackQuery.getString("data");
        long messageId = callbackQuery.getJSONObject("message").getLong("message_id");
        String chatId = callbackQuery.getJSONObject("message").getJSONObject("chat").getString("id");
        
        // Проверяем, что это админ
        if (!isAdmin(from.getLong("id"))) {
            answerCallbackQuery(callbackQuery.getString("id"), "У вас нет прав для этого действия!");
            return;
        }
        
        // Разбираем callback данные
        String[] parts = data.split(":");
        if (parts.length != 2) return;
        
        String action = parts[0];
        String requestId = parts[1];
        
        if (action.equals("approve")) {
            plugin.approveRequest(requestId);
            editMessageReplyMarkup(chatId, messageId);
            sendMessage(Long.parseLong(chatId), "✅ Заявка одобрена!");
        } else if (action.equals("reject")) {
            plugin.rejectRequest(requestId);
            editMessageReplyMarkup(chatId, messageId);
            sendMessage(Long.parseLong(chatId), "❌ Заявка отклонена!");
        }
        
        answerCallbackQuery(callbackQuery.getString("id"), "Действие выполнено!");
    }
    
    public void sendRequestToTopic(String requestId, String minecraftName, String telegramUsername, long telegramId) {
        String message = String.format(
            "📋 *Пришла заявка на вайтлист!*\n\n" +
            "👤 *Никнейм Minecraft:* `%s`\n" +
            "📱 *Telegram:* @%s\n" +
            "🆔 *ID:* `%d`",
            minecraftName, telegramUsername, telegramId
        );
        
        JSONObject replyMarkup = new JSONObject();
        JSONArray inlineKeyboard = new JSONArray();
        JSONArray row = new JSONArray();
        
        // Кнопка "Принять"
        JSONObject approveButton = new JSONObject();
        approveButton.put("text", "✅ Принять");
        approveButton.put("callback_data", "approve:" + requestId);
        
        // Кнопка "Отклонить"
        JSONObject rejectButton = new JSONObject();
        rejectButton.put("text", "❌ Отклонить");
        rejectButton.put("callback_data", "reject:" + requestId);
        
        row.put(approveButton);
        row.put(rejectButton);
        inlineKeyboard.put(row);
        replyMarkup.put("inline_keyboard", inlineKeyboard);
        
        sendMessageWithMarkup(chatId, message, replyMarkup.toString());
    }
    
    private void sendMessage(long chatId, String text) {
        sendMessage(String.valueOf(chatId), text);
    }
    
    private void sendMessage(String chatId, String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "Markdown");
            
            Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + botToken + "/sendMessage")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();
            
            client.newCall(request).execute().close();
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка отправки сообщения: " + e.getMessage());
        }
    }
    
    private void sendMessageWithMarkup(String chatId, String text, String replyMarkup) {
        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "Markdown");
            body.put("reply_markup", new JSONObject(replyMarkup));
            
            Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + botToken + "/sendMessage")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();
            
            client.newCall(request).execute().close();
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка отправки сообщения с разметкой: " + e.getMessage());
        }
    }
    
    private void answerCallbackQuery(String callbackQueryId, String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("callback_query_id", callbackQueryId);
            body.put("text", text);
            
            Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + botToken + "/answerCallbackQuery")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();
            
            client.newCall(request).execute().close();
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка ответа на callback: " + e.getMessage());
        }
    }
    
    private void editMessageReplyMarkup(String chatId, long messageId) {
        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("message_id", messageId);
            body.put("reply_markup", new JSONObject().put("inline_keyboard", new JSONArray()));
            
            Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + botToken + "/editMessageReplyMarkup")
                .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                .build();
            
            client.newCall(request).execute().close();
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка редактирования сообщения: " + e.getMessage());
        }
    }
    
    public void sendMessageToUser(long userId, String message) {
        sendMessage(userId, message);
    }
    
    private boolean isAdmin(long userId) {
        String userIdStr = String.valueOf(userId);
        return userIdStr.equals(adminChatId) || 
               plugin.getConfig().getStringList("admins").contains(userIdStr);
    }
    
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
        }
    }
}