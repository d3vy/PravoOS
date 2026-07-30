package com.pravoos.notification.push;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.event.LawyerDigestKafkaPayload;
import com.pravoos.notification.event.NewLoginKafkaPayload;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PushMessageFactory {

  private static final String CLIENT_SENDER_LABEL = "Клиент";
  private static final String LAWYER_SENDER_LABEL = "Ваш юрист";
  private static final String CLIENT_ROLE = "CLIENT";

  public PushMessage deadline(CaseDeadlineKafkaPayload payload) {
    return PushMessage.of(
        "Дедлайн по делу: " + payload.caseTitle(),
        String.format(
            "%s — %s. Осталось дней: %d",
            payload.deadlineTypeName(), payload.deadlineDate(), payload.daysLeft()),
        "/cases/" + payload.caseId(),
        "deadline-" + payload.caseId());
  }

  public PushMessage hearing(CaseHearingUpdatedKafkaPayload payload) {
    String previous =
        payload.previousHearingDate() == null ? "не было" : payload.previousHearingDate();
    return PushMessage.of(
        "Изменилась дата заседания",
        String.format("%s: %s → %s", payload.caseTitle(), previous, payload.newHearingDate()),
        "/cases/" + payload.caseId(),
        "hearing-" + payload.caseId());
  }

  public PushMessage caseMessage(
      CaseMessageCreatedKafkaPayload payload, boolean recipientIsLawyer) {
    String path = recipientIsLawyer ? "/cases/" : "/portal/cases/";
    return PushMessage.of(
        "Новое сообщение: " + payload.caseTitle(),
        senderLabel(payload.authorRole()) + ": " + preview(payload.preview()),
        path + payload.caseId(),
        "case-message-" + payload.caseId());
  }

  public PushMessage invoiceOverdue(InvoiceOverdueKafkaPayload payload) {
    return PushMessage.of(
        "Просрочен счёт № " + payload.invoiceNumber(),
        String.format(
            "%s — %s. Просрочка: %d дн.",
            payload.clientName(), payload.totalFormatted(), payload.daysOverdue()),
        "/invoices/" + payload.invoiceId(),
        "invoice-overdue-" + payload.invoiceId() + "-" + payload.daysOverdue());
  }

  public PushMessage morningDigest(LawyerDigestKafkaPayload payload) {
    List<String> parts = new ArrayList<>();
    if (payload.tasksTodayCount() > 0) {
      parts.add(payload.tasksTodayCount() + " " + pluralizeTasks(payload.tasksTodayCount()));
    }
    if (payload.upcomingDeadlinesCount() > 0) {
      parts.add(
          payload.upcomingDeadlinesCount()
              + " "
              + pluralizeDeadlines(payload.upcomingDeadlinesCount())
              + " на неделе");
    }
    if (payload.unpaidInvoicesCount() > 0) {
      parts.add(
          payload.unpaidInvoicesCount()
              + " "
              + pluralizeInvoices(payload.unpaidInvoicesCount())
              + " на "
              + payload.unpaidInvoicesTotalFormatted());
    }
    return PushMessage.of(
        "Утренний дайджест",
        String.join(", ", parts),
        "/dashboard",
        "digest-" + payload.lawyerId() + "-" + payload.digestDate());
  }

  private String pluralizeTasks(int count) {
    return count == 1 ? "задача на сегодня" : "задачи на сегодня";
  }

  private String pluralizeDeadlines(int count) {
    return count == 1 ? "дедлайн" : "дедлайна";
  }

  private String pluralizeInvoices(int count) {
    return count == 1 ? "неоплаченный счёт" : "неоплаченных счёта";
  }

  public PushMessage newLogin(NewLoginKafkaPayload payload) {
    return PushMessage.of(
        "Новый вход в аккаунт",
        String.format(
            "IP %s, %s. Если это не вы — смените пароль.",
            payload.ipAddress(), device(payload.userAgent())),
        "/settings",
        "new-login-" + payload.userId());
  }

  private String senderLabel(String authorRole) {
    return CLIENT_ROLE.equals(authorRole) ? CLIENT_SENDER_LABEL : LAWYER_SENDER_LABEL;
  }

  private String preview(String preview) {
    return preview == null || preview.isBlank() ? "Новое сообщение" : preview;
  }

  private String device(String userAgent) {
    return userAgent == null || userAgent.isBlank() ? "устройство неизвестно" : userAgent;
  }
}
