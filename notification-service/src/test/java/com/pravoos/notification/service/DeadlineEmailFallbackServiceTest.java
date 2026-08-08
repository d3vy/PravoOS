package com.pravoos.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.pravoos.notification.client.DeadlineEmailRequest;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.exception.NotificationDeliveryException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeadlineEmailFallbackServiceTest {

  @Mock private UserServiceClient userServiceClient;

  private DeadlineEmailFallbackService service;

  @BeforeEach
  void setUp() {
    service = new DeadlineEmailFallbackService(userServiceClient);
  }

  @Test
  void send_mapsPayloadToRequestAndDelegates() {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();
    CaseDeadlineKafkaPayload payload =
        new CaseDeadlineKafkaPayload(
            caseId, lawyerId, "Case A", "FILING_DEADLINE", "2026-08-10", 3);

    service.send(payload);

    ArgumentCaptor<DeadlineEmailRequest> captor =
        ArgumentCaptor.forClass(DeadlineEmailRequest.class);
    verify(userServiceClient).sendDeadlineEmail(captor.capture());
    DeadlineEmailRequest request = captor.getValue();
    assertThat(request.lawyerId()).isEqualTo(lawyerId);
    assertThat(request.caseId()).isEqualTo(caseId);
    assertThat(request.caseTitle()).isEqualTo("Case A");
    assertThat(request.deadlineTypeName()).isEqualTo("FILING_DEADLINE");
    assertThat(request.deadlineDate()).isEqualTo("2026-08-10");
    assertThat(request.daysLeft()).isEqualTo(3);
  }

  @Test
  void send_wrapsUserServiceFailureInNotificationDeliveryException() {
    UUID caseId = UUID.randomUUID();
    CaseDeadlineKafkaPayload payload =
        new CaseDeadlineKafkaPayload(
            caseId, UUID.randomUUID(), "Case A", "FILING_DEADLINE", "2026-08-10", 3);
    doThrow(new RuntimeException("user-service down"))
        .when(userServiceClient)
        .sendDeadlineEmail(any());

    assertThatThrownBy(() -> service.send(payload))
        .isInstanceOf(NotificationDeliveryException.class)
        .hasMessageContaining(caseId.toString())
        .hasCauseInstanceOf(RuntimeException.class);
  }
}
