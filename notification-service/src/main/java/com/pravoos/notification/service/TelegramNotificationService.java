package com.pravoos.notification.service;

import com.pravoos.notification.bot.PravoOsAdminBot;
import com.pravoos.notification.client.DeadlineEmailRequest;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.config.TelegramBotProperties;
import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Optional;

@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final PravoOsAdminBot bot;
    private final TelegramBotProperties botProperties;
    private final UserServiceClient userServiceClient;

    public TelegramNotificationService(PravoOsAdminBot bot,
                                       TelegramBotProperties botProperties,
                                       UserServiceClient userServiceClient) {
        this.bot = bot;
        this.botProperties = botProperties;
        this.userServiceClient = userServiceClient;
    }

    public void notifyNewApplication(ApplicationSubmittedKafkaPayload payload) {
        String text = formatApplicationMessage(payload);
        String context = payload.applicationId().toString();
        for (String adminChatId : botProperties.adminChatIds()) {
            SendMessage message = new SendMessage();
            message.setChatId(adminChatId);
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(buildApprovalKeyboard(payload));
            sendSafely(message, context);
        }
    }

    public void notifyDeadline(CaseDeadlineKafkaPayload payload) {
        Optional<Long> chatId = userServiceClient.resolveTelegramChatId(payload.lawyerId());
        if (chatId.isEmpty()) {
            log.info("Lawyer {} has no linked Telegram, falling back to email for case {}",
                    payload.lawyerId(), payload.caseId());
            sendDeadlineEmailFallback(payload);
            return;
        }
        SendMessage message = new SendMessage();
        message.setChatId(chatId.get().toString());
        message.setText(formatDeadlineMessage(payload));
        message.setParseMode("HTML");
        sendSafely(message, payload.caseId().toString());
    }

    private void sendDeadlineEmailFallback(CaseDeadlineKafkaPayload payload) {
        try {
            userServiceClient.sendDeadlineEmail(new DeadlineEmailRequest(
                    payload.lawyerId(),
                    payload.caseId(),
                    payload.caseTitle(),
                    payload.deadlineTypeName(),
                    payload.deadlineDate(),
                    payload.daysLeft()));
        } catch (Exception e) {
            log.error("Failed to request deadline email for lawyer {} case {}: {}",
                    payload.lawyerId(), payload.caseId(), e.getMessage());
        }
    }

    private String formatDeadlineMessage(CaseDeadlineKafkaPayload payload) {
        return String.format("""
                <b>Напоминание о дедлайне</b>

                <b>Дело:</b> %s
                <b>%s:</b> %s
                Осталось дней: %d""",
                escapeHtml(payload.caseTitle()),
                escapeHtml(payload.deadlineTypeName()),
                escapeHtml(payload.deadlineDate()),
                payload.daysLeft());
    }

    private void sendSafely(SendMessage message, String context) {
        try {
            bot.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram notification for context [{}]: {}", context, e.getMessage());
        }
    }

    private InlineKeyboardMarkup buildApprovalKeyboard(ApplicationSubmittedKafkaPayload payload) {
        InlineKeyboardButton approveButton = new InlineKeyboardButton();
        approveButton.setText("Принять");
        approveButton.setCallbackData("approve:" + payload.applicationId());

        InlineKeyboardButton rejectButton = new InlineKeyboardButton();
        rejectButton.setText("Отклонить");
        rejectButton.setCallbackData("reject:" + payload.applicationId());

        InlineKeyboardButton approveForceButton = new InlineKeyboardButton();
        approveForceButton.setText("Принять без почты");
        approveForceButton.setCallbackData("approve_force:" + payload.applicationId());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(approveButton, rejectButton), List.of(approveForceButton)));
        return markup;
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

                ID заявки: <code>%s</code>""",
                escapeHtml(payload.fullName()),
                escapeHtml(payload.email()),
                escapeHtml(specialization),
                payload.applicationId()
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
