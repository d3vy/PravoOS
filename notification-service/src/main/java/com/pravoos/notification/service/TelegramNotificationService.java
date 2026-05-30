package com.pravoos.notification.service;

import com.pravoos.notification.bot.PravoOsAdminBot;
import com.pravoos.notification.config.TelegramBotProperties;
import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final PravoOsAdminBot bot;
    private final TelegramBotProperties botProperties;

    public TelegramNotificationService(PravoOsAdminBot bot, TelegramBotProperties botProperties) {
        this.bot = bot;
        this.botProperties = botProperties;
    }

    public void notifyNewApplication(ApplicationSubmittedKafkaPayload payload) {
        SendMessage message = buildMessage(botProperties.adminChatId(), formatApplicationMessage(payload));
        sendSafely(message, payload.applicationId().toString());
    }

    private void sendSafely(SendMessage message, String context) {
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram notification for context [{}]: {}", context, e.getMessage());
        }
    }

    private SendMessage buildMessage(String chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setParseMode("HTML");
        return message;
    }

    private String formatApplicationMessage(ApplicationSubmittedKafkaPayload payload) {
        String specialization = payload.specialization() != null && !payload.specialization().isBlank()
                ? payload.specialization()
                : "не указана";
        return String.format("""
                <b>Новая заявка от юриста</b>

                <b>Имя:</b> %s
                <b>Email:</b> %s
                <b>Специализация:</b> %s

                ID заявки: <code>%s</code>

                Рассмотреть в панели администратора.""",
                payload.fullName(),
                payload.email(),
                specialization,
                payload.applicationId()
        );
    }
}
