package com.pravoos.notification.service;

import com.pravoos.notification.client.CaseMessageNotificationRequest;
import com.pravoos.notification.client.CaseMessageNotificationResult;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseHearingUpdatedKafkaPayload;
import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.event.InvoiceOverdueKafkaPayload;
import com.pravoos.notification.event.InvoicePaidKafkaPayload;
import com.pravoos.notification.event.LawyerDigestKafkaPayload;
import com.pravoos.notification.event.MailboxSyncPausedKafkaPayload;
import com.pravoos.notification.event.NewLoginKafkaPayload;
import com.pravoos.notification.push.PushMessageFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatcher {

  private static final String CHANNEL_PUSH = "push";
  private static final String CHANNEL_TELEGRAM = "telegram";
  private static final String CHANNEL_EMAIL = "email";

  private final TelegramNotificationService telegramNotificationService;
  private final PushNotificationService pushNotificationService;
  private final DeadlineEmailFallbackService deadlineEmailFallbackService;
  private final UserServiceClient userServiceClient;
  private final PushMessageFactory pushMessageFactory;
  private final ChannelDelivery channelDelivery;

  public NotificationDispatcher(
      TelegramNotificationService telegramNotificationService,
      PushNotificationService pushNotificationService,
      DeadlineEmailFallbackService deadlineEmailFallbackService,
      UserServiceClient userServiceClient,
      PushMessageFactory pushMessageFactory,
      ChannelDelivery channelDelivery) {
    this.telegramNotificationService = telegramNotificationService;
    this.pushNotificationService = pushNotificationService;
    this.deadlineEmailFallbackService = deadlineEmailFallbackService;
    this.userServiceClient = userServiceClient;
    this.pushMessageFactory = pushMessageFactory;
    this.channelDelivery = channelDelivery;
  }

  public void dispatchDeadline(
      String eventType, String dedupKey, CaseDeadlineKafkaPayload payload) {
    ChannelDelivery.Batch batch = channelDelivery.batch(eventType, dedupKey);
    boolean pushed =
        batch.reachedRecipient(
            CHANNEL_PUSH,
            () ->
                pushNotificationService.notifyUser(
                        payload.lawyerId(), pushMessageFactory.deadline(payload))
                    > 0);
    boolean telegramDelivered =
        batch.reachedRecipient(
            CHANNEL_TELEGRAM, () -> telegramNotificationService.sendDeadline(payload));
    if (!pushed && !telegramDelivered) {
      batch.deliver(CHANNEL_EMAIL, () -> deadlineEmailFallbackService.send(payload));
    }
    batch.complete();
  }

  public void dispatchHearingUpdate(
      String eventType, String dedupKey, CaseHearingUpdatedKafkaPayload payload) {
    channelDelivery
        .batch(eventType, dedupKey)
        .deliver(
            CHANNEL_PUSH,
            () ->
                pushNotificationService.notifyUser(
                    payload.lawyerId(), pushMessageFactory.hearing(payload)))
        .deliver(CHANNEL_TELEGRAM, () -> telegramNotificationService.sendHearingUpdate(payload))
        .complete();
  }

  public void dispatchCaseMessage(
      String eventType, String dedupKey, CaseMessageCreatedKafkaPayload payload) {
    CaseMessageNotificationResult result =
        userServiceClient.dispatchCaseMessage(
            new CaseMessageNotificationRequest(
                payload.caseId(),
                payload.caseTitle(),
                payload.authorRole(),
                payload.recipientLawyerId(),
                payload.recipientClientId(),
                payload.preview()));
    ChannelDelivery.Batch batch = channelDelivery.batch(eventType, dedupKey);
    if (result.pushEnabled()) {
      boolean recipientIsLawyer = payload.recipientLawyerId() != null;
      batch.deliver(
          CHANNEL_PUSH,
          () ->
              pushNotificationService.notifyUser(
                  result.recipientUserId(),
                  pushMessageFactory.caseMessage(payload, recipientIsLawyer)));
    }
    if (result.telegramChatId() != null) {
      batch.deliver(
          CHANNEL_TELEGRAM,
          () -> telegramNotificationService.sendCaseMessage(payload, result.telegramChatId()));
    }
    batch.complete();
  }

  public void dispatchInvoiceOverdue(
      String eventType, String dedupKey, InvoiceOverdueKafkaPayload payload) {
    channelDelivery
        .batch(eventType, dedupKey)
        .deliver(
            CHANNEL_PUSH,
            () ->
                pushNotificationService.notifyUser(
                    payload.lawyerId(), pushMessageFactory.invoiceOverdue(payload)))
        .complete();
  }

  public void dispatchInvoicePaid(
      String eventType, String dedupKey, InvoicePaidKafkaPayload payload) {
    channelDelivery
        .batch(eventType, dedupKey)
        .deliver(
            CHANNEL_PUSH,
            () ->
                pushNotificationService.notifyUser(
                    payload.lawyerId(), pushMessageFactory.invoicePaid(payload)))
        .complete();
  }

  public void dispatchMorningDigest(
      String eventType, String dedupKey, LawyerDigestKafkaPayload payload) {
    channelDelivery
        .batch(eventType, dedupKey)
        .deliver(
            CHANNEL_PUSH,
            () ->
                pushNotificationService.notifyUser(
                    payload.lawyerId(), pushMessageFactory.morningDigest(payload)))
        .complete();
  }

  public void dispatchNewLogin(String eventType, String dedupKey, NewLoginKafkaPayload payload) {
    ChannelDelivery.Batch batch = channelDelivery.batch(eventType, dedupKey);
    if (payload.pushEnabled()) {
      batch.deliver(
          CHANNEL_PUSH,
          () ->
              pushNotificationService.notifyUser(
                  payload.userId(), pushMessageFactory.newLogin(payload)));
    }
    if (payload.telegramEnabled()) {
      batch.deliver(CHANNEL_TELEGRAM, () -> telegramNotificationService.sendNewLogin(payload));
    }
    batch.complete();
  }

  public void dispatchMailboxPaused(
      String eventType, String dedupKey, MailboxSyncPausedKafkaPayload payload) {
    channelDelivery
        .batch(eventType, dedupKey)
        .deliver(
            CHANNEL_PUSH,
            () ->
                pushNotificationService.notifyUser(
                    payload.lawyerId(), pushMessageFactory.mailboxPaused(payload)))
        .deliver(CHANNEL_TELEGRAM, () -> telegramNotificationService.sendMailboxPaused(payload))
        .complete();
  }
}
