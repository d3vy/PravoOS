package com.pravoos.notification.service;

import com.pravoos.notification.client.DeadlineEmailRequest;
import com.pravoos.notification.client.UserServiceClient;
import com.pravoos.notification.event.CaseDeadlineKafkaPayload;
import com.pravoos.notification.exception.NotificationDeliveryException;
import org.springframework.stereotype.Service;

@Service
public class DeadlineEmailFallbackService {

  private final UserServiceClient userServiceClient;

  public DeadlineEmailFallbackService(UserServiceClient userServiceClient) {
    this.userServiceClient = userServiceClient;
  }

  public void send(CaseDeadlineKafkaPayload payload) {
    try {
      userServiceClient.sendDeadlineEmail(
          new DeadlineEmailRequest(
              payload.lawyerId(),
              payload.caseId(),
              payload.caseTitle(),
              payload.deadlineTypeName(),
              payload.deadlineDate(),
              payload.daysLeft()));
    } catch (Exception e) {
      throw new NotificationDeliveryException(
          "Failed to request deadline email for case " + payload.caseId(), e);
    }
  }
}
