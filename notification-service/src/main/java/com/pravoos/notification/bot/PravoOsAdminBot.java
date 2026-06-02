package com.pravoos.notification.bot;

import com.pravoos.notification.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
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

    private final String botUsername;
    private final ApplicationCallbackHandler callbackHandler;

    public PravoOsAdminBot(TelegramBotProperties botProperties, ApplicationCallbackHandler callbackHandler) {
        super(botProperties.token());
        this.botUsername = botProperties.username();
        this.callbackHandler = callbackHandler;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
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
        editMessage.setText(originalText + "\n\n<b>Статус:</b> " + statusText);
        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message after callback: {}", e.getMessage());
        }
    }
}
