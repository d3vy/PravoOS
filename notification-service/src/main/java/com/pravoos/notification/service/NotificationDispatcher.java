package com.pravoos.notification.service;

import com.pravoos.notification.client.CaseMessageNotificationRequest;
import com.pravoos.notification.client.CaseMessageNotificationResult;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.event.LawyerDigestKafkaPayload;
import com.pravoos.notification.event.NewLoginKafkaPayload;
import com.pravoos.notification.push.PushMessageFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {

  private final TelegramNotificationService telegramNotificationService;
  private final PushNotificationService pushNotificationService;
  private final DeadlineEmailFallbackService deadlineEmailFallbackService;
  private final UserServiceClient userServiceClient;
  private final PushMessageFactory pushMessageFactory;

  public NotificationDispatcher(
      TelegramNotificationService telegramNotificationService,
      PushNotificationService pushNotificationService,
      DeadlineEmailFallbackService deadlineEmailFallbackService,
      UserServiceClient userServiceClient,
      PushMessageFactory pushMessageFactory) {
    this.telegramNotificationService = telegramNotificationService;
    this.pushNotificationService = pushNotificationService;
    this.deadlineEmailFallbackService = deadlineEmailFallbackService;
    this.userServiceClient = userServiceClient;
    this.pushMessageFactory = pushMessageFactory;
  }

  public void dispatchDeadline(CaseDeadlineKafkaPayload payload) {
    int pushed =
        pushNotificationService.notifyUser(
            payload.lawyerId(), pushMessageFactory.deadline(payload));
    boolean telegramDelivered = telegramNotificationService.sendDeadline(payload);
    if (!telegramDelivered && pushed == 0) {
      deadlineEmailFallbackService.send(payload);
    }
  }

  public void dispatchHearingUpdate(CaseHearingUpdatedKafkaPayload payload) {
    pushNotificationService.notifyUser(payload.lawyerId(), pushMessageFactory.hearing(payload));
    telegramNotificationService.sendHearingUpdate(payload);
  }

  public void dispatchCaseMessage(CaseMessageCreatedKafkaPayload payload) {
    CaseMessageNotificationResult result =
        userServiceClient.dispatchCaseMessage(
            new CaseMessageNotificationRequest(
                payload.caseId(),
                payload.caseTitle(),
                payload.authorRole(),
                payload.recipientLawyerId(),
                payload.recipientClientId(),
                payload.preview()));
    if (result.pushEnabled()) {
      boolean recipientIsLawyer = payload.recipientLawyerId() != null;
      pushNotificationService.notifyUser(
          result.recipientUserId(), pushMessageFactory.caseMessage(payload, recipientIsLawyer));
    }
    if (result.telegramChatId() != null) {
      telegramNotificationService.sendCaseMessage(payload, result.telegramChatId());
    }
  }

  public void dispatchInvoiceOverdue(InvoiceOverdueKafkaPayload payload) {
    pushNotificationService.notifyUser(
        payload.lawyerId(), pushMessageFactory.invoiceOverdue(payload));
  }

  public void dispatchMorningDigest(LawyerDigestKafkaPayload payload) {
    pushNotificationService.notifyUser(
        payload.lawyerId(), pushMessageFactory.morningDigest(payload));
  }

  public void dispatchNewLogin(NewLoginKafkaPayload payload) {
    if (payload.pushEnabled()) {
      pushNotificationService.notifyUser(payload.userId(), pushMessageFactory.newLogin(payload));
    }
    if (payload.telegramEnabled()) {
      telegramNotificationService.sendNewLogin(payload);
    }
  }
}
