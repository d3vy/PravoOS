package com.pravoos.notification.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.notification.client.ApplicationDetailsResponse;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.event.ApplicationSubmittedEvent;
import com.pravoos.notification.event.ApplicationSubmittedKafkaPayload;
import com.pravoos.notification.service.ProcessedEventGuard;
import com.pravoos.notification.service.TelegramNotificationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationEventConsumerTest {

  private static final String EVENT_TYPE = "application.submitted";

  @Mock private TelegramNotificationService telegramNotificationService;
  @Mock private UserServiceClient userServiceClient;
  @Mock private ProcessedEventGuard processedEventGuard;

  private ApplicationEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new ApplicationEventConsumer(
            telegramNotificationService, userServiceClient, processedEventGuard);
  }

  @Test
  void onApplicationSubmitted_notifiesOnce() {
    UUID applicationId = UUID.randomUUID();
    ApplicationDetailsResponse details =
        new ApplicationDetailsResponse("Ivan Ivanov", "ivan@example.com", "Corporate law");
    when(processedEventGuard.claim(eq(EVENT_TYPE), eq(applicationId.toString()))).thenReturn(true);
    when(userServiceClient.getApplication(applicationId)).thenReturn(Optional.of(details));

    consumer.onApplicationSubmitted(new ApplicationSubmittedEvent(applicationId));

    verify(telegramNotificationService)
        .notifyNewApplication(
            new ApplicationSubmittedKafkaPayload(
                applicationId, "Ivan Ivanov", "ivan@example.com", "Corporate law"));
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }

  @Test
  void onApplicationSubmitted_skipsDuplicate() {
    UUID applicationId = UUID.randomUUID();
    when(processedEventGuard.claim(eq(EVENT_TYPE), eq(applicationId.toString()))).thenReturn(false);

    consumer.onApplicationSubmitted(new ApplicationSubmittedEvent(applicationId));

    verify(userServiceClient, never()).getApplication(any());
    verify(telegramNotificationService, never()).notifyNewApplication(any());
  }

  @Test
  void onApplicationSubmitted_applicationNoLongerExists_keepsClaimWithoutNotifying() {
    UUID applicationId = UUID.randomUUID();
    when(processedEventGuard.claim(eq(EVENT_TYPE), eq(applicationId.toString()))).thenReturn(true);
    when(userServiceClient.getApplication(applicationId)).thenReturn(Optional.empty());

    consumer.onApplicationSubmitted(new ApplicationSubmittedEvent(applicationId));

    verify(telegramNotificationService, never()).notifyNewApplication(any());
    verify(processedEventGuard, never()).release(anyString(), anyString());
  }
}
