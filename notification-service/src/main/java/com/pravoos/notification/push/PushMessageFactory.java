package com.pravoos.notification.push;

import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.event.NewLoginKafkaPayload;
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
