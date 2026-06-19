package com.pravoos.notification.bot;

import com.pravoos.notification.client.TelegramBindException;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class PravoOsAdminBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(PravoOsAdminBot.class);

    private static final String START_COMMAND = "/start";

    private final String botUsername;
    private final ApplicationCallbackHandler callbackHandler;
    private final UserServiceClient userServiceClient;
    private final Set<String> allowedChatIds;

    public PravoOsAdminBot(TelegramBotProperties botProperties,
                           ApplicationCallbackHandler callbackHandler,
                           UserServiceClient userServiceClient) {
        super(botProperties.token());
        this.botUsername = botProperties.username();
        this.callbackHandler = callbackHandler;
        this.userServiceClient = userServiceClient;
        List<String> configuredChatIds = botProperties.adminChatIds();
        this.allowedChatIds = configuredChatIds == null ? Set.of() : Set.copyOf(configuredChatIds);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        MDC.put("requestId", UUID.randomUUID().toString());
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            }
        } finally {
            MDC.remove("requestId");
        }
    }

    private void handleTextMessage(Message message) {
        Long chatId = message.getChatId();
        String text = message.getText().trim();

        if (!text.startsWith(START_COMMAND)) {
            sendText(chatId, "Чтобы получать уведомления, откройте ссылку привязки из своего профиля в PravoOS.");
            return;
        }

        String code = text.substring(START_COMMAND.length()).trim();
        if (code.isEmpty()) {
            sendText(chatId, "Откройте ссылку привязки из профиля PravoOS — она содержит код привязки.");
            return;
        }

        String fullName;
        try {
            fullName = userServiceClient.bindTelegram(code, chatId);
        } catch (TelegramBindException e) {
            sendText(chatId, e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Failed to bind Telegram for chat {}: {}", chatId, e.getMessage());
            sendText(chatId, "Не удалось привязать уведомления. Попробуйте позже.");
            return;
        }

        String greeting = (fullName == null || fullName.isBlank()) ? "" : ", " + fullName;
        sendText(chatId, "Уведомления привязаны" + greeting
                + ". Напоминания о дедлайнах по делам будут приходить сюда.");
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        MaybeInaccessibleMessage maybeMessage = callbackQuery.getMessage();
        if (!(maybeMessage instanceof Message originalMessage)) {
            log.warn("Received callback with inaccessible message, ignoring");
            return;
        }
        String chatId = originalMessage.getChatId().toString();
        Integer messageId = originalMessage.getMessageId();

        if (!allowedChatIds.contains(chatId)) {
            log.warn("Rejected callback from unauthorized chat {}: {}", chatId, data);
            answerCallbackQuery(callbackQuery.getId(), "Доступ запрещён");
            return;
        }

        if (!callbackHandler.supports(data)) {
            answerCallbackQuery(callbackQuery.getId(), "Неизвестная команда");
            return;
        }

        String result;
        try {
            result = callbackHandler.handle(data);
        } catch (Exception e) {
            log.error("Failed to handle callback [{}]: {}", data, e.getMessage());
            answerCallbackQuery(callbackQuery.getId(), "Ошибка при обработке");
            return;
        }

        answerCallbackQuery(callbackQuery.getId(), result);
        removeInlineKeyboard(chatId, messageId, originalMessage.getText(), result);
    }

    private void sendText(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {}: {}", chatId, e.getMessage());
        }
    }

    private void answerCallbackQuery(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query: {}", e.getMessage());
        }
    }

    private void removeInlineKeyboard(String chatId, Integer messageId, String originalText, String statusText) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId);
        editMessage.setMessageId(messageId);
        editMessage.setParseMode("HTML");
        String safeOriginalText = originalText != null ? originalText : "";
        editMessage.setText(safeOriginalText + "\n\n<b>Статус:</b> " + statusText);
        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message after callback: {}", e.getMessage());
        }
    }
}
