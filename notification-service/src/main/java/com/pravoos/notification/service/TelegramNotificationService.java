package com.pravoos.notification.service;

import com.pravoos.notification.bot.PravoOsAdminBot;
import com.pravoos.notification.config.TelegramBotProperties;
import com.pravoos.notification.event.*;
import com.pravoos.notification.exception.NotificationDeliveryException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Service
public class TelegramNotificationService {

  private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

  private final PravoOsAdminBot bot;
  private final TelegramBotProperties botProperties;
  private final TelegramChatIdResolver telegramChatIdResolver;

  public TelegramNotificationService(
      PravoOsAdminBot bot,
      TelegramBotProperties botProperties,
      TelegramChatIdResolver telegramChatIdResolver) {
    this.bot = bot;
    this.botProperties = botProperties;
    this.telegramChatIdResolver = telegramChatIdResolver;
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

  public boolean sendDeadline(CaseDeadlineKafkaPayload payload) {
    Optional<Long> chatId = telegramChatIdResolver.resolve(payload.lawyerId());
    if (chatId.isEmpty()) {
      log.info(
          "Lawyer {} has no linked Telegram for deadline of case {}",
          payload.lawyerId(),
          payload.caseId());
      return false;
    }
    send(buildMessage(chatId.get(), formatDeadlineMessage(payload)), payload.caseId().toString());
    return true;
  }

  public boolean sendHearingUpdate(CaseHearingUpdatedKafkaPayload payload) {
    Optional<Long> chatId = telegramChatIdResolver.resolve(payload.lawyerId());
    if (chatId.isEmpty()) {
      log.info(
          "Lawyer {} has no linked Telegram, skipping hearing update for case {}",
          payload.lawyerId(),
          payload.caseId());
      return false;
    }
    send(buildMessage(chatId.get(), formatHearingMessage(payload)), payload.caseId().toString());
    return true;
  }

  public void sendCaseMessage(CaseMessageCreatedKafkaPayload payload, long chatId) {
    send(buildMessage(chatId, formatCaseMessage(payload)), payload.caseId().toString());
  }

  public boolean sendNewLogin(NewLoginKafkaPayload payload) {
    Optional<Long> chatId = telegramChatIdResolver.resolve(payload.userId());
    if (chatId.isEmpty()) {
      log.info("User {} has no linked Telegram, skipping new-login alert", payload.userId());
      return false;
    }
    send(buildMessage(chatId.get(), formatNewLoginMessage(payload)), payload.userId().toString());
    return true;
  }

  public boolean sendMailboxPaused(MailboxSyncPausedKafkaPayload payload) {
    Optional<Long> chatId = telegramChatIdResolver.resolve(payload.lawyerId());
    if (chatId.isEmpty()) {
      log.info(
          "Lawyer {} has no linked Telegram, skipping mailbox-paused alert for {}",
          payload.lawyerId(),
          payload.mailboxId());
      return false;
    }
    send(
        buildMessage(chatId.get(), formatMailboxPausedMessage(payload)),
        payload.mailboxId().toString());
    return true;
  }

  private String formatMailboxPausedMessage(MailboxSyncPausedKafkaPayload payload) {
    return String.format(
        """
                <b>Синхронизация почты остановлена</b>

                <b>Ящик:</b> %s
                <b>Ошибок подряд:</b> %d
                <b>Последняя ошибка:</b> %s

                Проверьте пароль приложения в настройках почты PravoOS и включите синхронизацию заново.""",
        escapeHtml(payload.emailAddress()), payload.failures(), escapeHtml(payload.lastError()));
  }

  private SendMessage buildMessage(long chatId, String text) {
    SendMessage message = new SendMessage();
    message.setChatId(Long.toString(chatId));
    message.setText(text);
    message.setParseMode("HTML");
    return message;
  }

  private String formatNewLoginMessage(NewLoginKafkaPayload payload) {
    String userAgent =
        payload.userAgent() == null || payload.userAgent().isBlank()
            ? "неизвестно"
            : payload.userAgent();
    return String.format(
        """
                <b>Новый вход в аккаунт</b>

                <b>Время (UTC):</b> %s
                <b>IP:</b> %s
                <b>Устройство:</b> %s

                Если это были не вы — смените пароль и завершите сессии в настройках.""",
        escapeHtml(payload.occurredAt()), escapeHtml(payload.ipAddress()), escapeHtml(userAgent));
  }

  private String formatHearingMessage(CaseHearingUpdatedKafkaPayload payload) {
    String previous =
        payload.previousHearingDate() == null ? "не было" : payload.previousHearingDate();
    return String.format(
        """
                <b>Изменилась дата заседания</b>

                <b>Дело:</b> %s
                <b>Номер дела:</b> %s
                <b>Было:</b> %s
                <b>Стало:</b> %s""",
        escapeHtml(payload.caseTitle()),
        escapeHtml(payload.courtCaseNumber()),
        escapeHtml(previous),
        escapeHtml(payload.newHearingDate()));
  }

  private String formatDeadlineMessage(CaseDeadlineKafkaPayload payload) {
    return String.format(
        """
                <b>Напоминание о дедлайне</b>

                <b>Дело:</b> %s
                <b>%s:</b> %s
                Осталось дней: %d""",
        escapeHtml(payload.caseTitle()),
        escapeHtml(payload.deadlineTypeName()),
        escapeHtml(payload.deadlineDate()),
        payload.daysLeft());
  }

  private String formatCaseMessage(CaseMessageCreatedKafkaPayload payload) {
    String senderLabel = "CLIENT".equals(payload.authorRole()) ? "Клиент" : "Ваш юрист";
    return String.format(
        """
                <b>Новое сообщение по делу</b>

                <b>Дело:</b> %s
                <b>От:</b> %s

                %s""",
        escapeHtml(payload.caseTitle()), escapeHtml(senderLabel), escapeHtml(payload.preview()));
  }

  private void send(SendMessage message, String context) {
    try {
      bot.execute(message);
    } catch (TelegramApiException e) {
      throw new NotificationDeliveryException(
          "Failed to send Telegram notification for context [" + context + "]", e);
    }
  }

  private void sendSafely(SendMessage message, String context) {
    try {
      bot.execute(message);
    } catch (TelegramApiException e) {
      log.error(
          "Failed to send Telegram notification for context [{}]: {}", context, e.getMessage());
    }
  }

  private InlineKeyboardMarkup buildApprovalKeyboard(ApplicationSubmittedKafkaPayload payload) {
    InlineKeyboardButton approveButton = new InlineKeyboardButton();
    approveButton.setText("Принять");
    approveButton.setCallbackData("approve:" + payload.applicationId());

    InlineKeyboardButton rejectButton = new InlineKeyboardButton();
    rejectButton.setText("Отклонить");
    rejectButton.setCallbackData("reject:" + payload.applicationId());

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    markup.setKeyboard(List.of(List.of(approveButton, rejectButton)));
    return markup;
  }

  private String formatApplicationMessage(ApplicationSubmittedKafkaPayload payload) {
    String specialization =
        payload.specialization() != null && !payload.specialization().isBlank()
            ? payload.specialization()
            : "не указана";
    return String.format(
        """
                <b>Новая заявка от юриста</b>

                <b>Имя:</b> %s
                <b>Email:</b> %s
                <b>Специализация:</b> %s

                ID заявки: <code>%s</code>""",
        escapeHtml(payload.fullName()),
        escapeHtml(payload.email()),
        escapeHtml(specialization),
        payload.applicationId());
  }

  private String escapeHtml(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
