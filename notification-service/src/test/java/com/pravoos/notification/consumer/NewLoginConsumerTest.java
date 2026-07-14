package com.pravoos.notification.consumer;

import com.pravoos.notification.event.NewLoginKafkaPayload;
import com.pravoos.notification.service.NotificationDispatcher;
import com.pravoos.notification.service.ProcessedEventGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewLoginConsumerTest {

    private static final String EVENT_TYPE = "user.new_login";

    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private ProcessedEventGuard processedEventGuard;

    private NewLoginConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NewLoginConsumer(notificationDispatcher, processedEventGuard);
    }

    @Test
    void onNewLogin_notifiesAndMarksProcessed() {
        NewLoginKafkaPayload payload = payload();
        when(processedEventGuard.isProcessed(eq(EVENT_TYPE), anyString())).thenReturn(false);

        consumer.onNewLogin(payload);

        verify(notificationDispatcher).dispatchNewLogin(payload);
        verify(processedEventGuard).markProcessed(eq(EVENT_TYPE), anyString());
    }

    @Test
    void onNewLogin_skipsDuplicate() {
        NewLoginKafkaPayload payload = payload();
        when(processedEventGuard.isProcessed(eq(EVENT_TYPE), anyString())).thenReturn(true);

        consumer.onNewLogin(payload);

        verify(notificationDispatcher, never()).dispatchNewLogin(payload);
        verify(processedEventGuard, never()).markProcessed(anyString(), anyString());
    }

    private NewLoginKafkaPayload payload() {
        return new NewLoginKafkaPayload(UUID.randomUUID(), "203.0.113.9", "JUnit-UA", "2026-07-03 10:15", true, true);
    }
}
