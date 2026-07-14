package com.pravoos.notification.service;

import com.pravoos.notification.client.CaseMessageNotificationRequest;
import com.pravoos.notification.client.CaseMessageNotificationResult;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.event.CaseMessageCreatedKafkaPayload;
import com.pravoos.notification.event.NewLoginKafkaPayload;
import com.pravoos.notification.push.PushMessage;
import com.pravoos.notification.push.PushMessageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock private TelegramNotificationService telegramNotificationService;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private DeadlineEmailFallbackService deadlineEmailFallbackService;
    @Mock private UserServiceClient userServiceClient;

    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(telegramNotificationService, pushNotificationService,
                deadlineEmailFallbackService, userServiceClient, new PushMessageFactory());
    }

    @Test
    void dispatchDeadline_sendsPushAndTelegram_withoutEmailFallback() {
        CaseDeadlineKafkaPayload payload = deadlinePayload();
        when(pushNotificationService.notifyUser(eq(payload.lawyerId()), any(PushMessage.class))).thenReturn(2);
        when(telegramNotificationService.sendDeadline(payload)).thenReturn(true);

        dispatcher.dispatchDeadline(payload);

        verify(pushNotificationService).notifyUser(eq(payload.lawyerId()), any(PushMessage.class));
        verify(telegramNotificationService).sendDeadline(payload);
        verify(deadlineEmailFallbackService, never()).send(any());
    }

    @Test
    void dispatchDeadline_fallsBackToEmail_whenNoTelegramAndNoPushDevices() {
        CaseDeadlineKafkaPayload payload = deadlinePayload();
        when(pushNotificationService.notifyUser(eq(payload.lawyerId()), any(PushMessage.class))).thenReturn(0);
        when(telegramNotificationService.sendDeadline(payload)).thenReturn(false);

        dispatcher.dispatchDeadline(payload);

        verify(deadlineEmailFallbackService).send(payload);
    }

    @Test
    void dispatchDeadline_skipsEmail_whenOnlyPushDelivered() {
        CaseDeadlineKafkaPayload payload = deadlinePayload();
        when(pushNotificationService.notifyUser(eq(payload.lawyerId()), any(PushMessage.class))).thenReturn(1);
        when(telegramNotificationService.sendDeadline(payload)).thenReturn(false);

        dispatcher.dispatchDeadline(payload);

        verify(deadlineEmailFallbackService, never()).send(any());
    }

    @Test
    void dispatchCaseMessage_pushesToRecipient_andSendsTelegram() {
        CaseMessageCreatedKafkaPayload payload = caseMessagePayload();
        UUID recipientId = UUID.randomUUID();
        when(userServiceClient.dispatchCaseMessage(any(CaseMessageNotificationRequest.class)))
                .thenReturn(new CaseMessageNotificationResult(recipientId, 555L, true));

        dispatcher.dispatchCaseMessage(payload);

        verify(pushNotificationService).notifyUser(eq(recipientId), any(PushMessage.class));
        verify(telegramNotificationService).sendCaseMessage(payload, 555L);
    }

    @Test
    void dispatchCaseMessage_skipsChannels_whenRecipientOptedOut() {
        CaseMessageCreatedKafkaPayload payload = caseMessagePayload();
        when(userServiceClient.dispatchCaseMessage(any(CaseMessageNotificationRequest.class)))
                .thenReturn(CaseMessageNotificationResult.none());

        dispatcher.dispatchCaseMessage(payload);

        verify(pushNotificationService, never()).notifyUser(any(), any());
        verify(telegramNotificationService, never()).sendCaseMessage(any(), anyLong());
    }

    @Test
    void dispatchNewLogin_honoursChannelFlags() {
        NewLoginKafkaPayload pushOnly = new NewLoginKafkaPayload(
                UUID.randomUUID(), "203.0.113.9", "JUnit-UA", "2026-07-03 10:15", false, true);

        dispatcher.dispatchNewLogin(pushOnly);

        verify(pushNotificationService).notifyUser(eq(pushOnly.userId()), any(PushMessage.class));
        verify(telegramNotificationService, never()).sendNewLogin(any());
    }

    @Test
    void dispatchNewLogin_skipsPush_whenDisabled() {
        NewLoginKafkaPayload telegramOnly = new NewLoginKafkaPayload(
                UUID.randomUUID(), "203.0.113.9", "JUnit-UA", "2026-07-03 10:15", true, false);

        dispatcher.dispatchNewLogin(telegramOnly);

        verify(pushNotificationService, never()).notifyUser(any(), any());
        verify(telegramNotificationService).sendNewLogin(telegramOnly);
    }

    private CaseDeadlineKafkaPayload deadlinePayload() {
        return new CaseDeadlineKafkaPayload(UUID.randomUUID(), UUID.randomUUID(), "Иванов против ООО",
                "Подача апелляции", "2026-08-01", 3);
    }

    private CaseMessageCreatedKafkaPayload caseMessagePayload() {
        return new CaseMessageCreatedKafkaPayload(UUID.randomUUID(), "Иванов против ООО", UUID.randomUUID(),
                "CLIENT", UUID.randomUUID(), UUID.randomUUID(), null, "Добрый день");
    }
}
