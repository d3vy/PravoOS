package com.pravoos.user.collaboration.internal.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.collaboration.internal.dto.DeadlineEmailRequest;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.email.ResendEmailClient;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeadlineNotificationEmailServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ResendEmailClient resendEmailClient;

  private ResendProperties properties;
  private DeadlineNotificationEmailService service;

  @BeforeEach
  void setUp() {
    properties =
        new ResendProperties("key", "from@pravoos.ru", "https://app.pravoos.ru", 24, 1, 100);
    service = new DeadlineNotificationEmailService(userRepository, resendEmailClient, properties);
  }

  @Test
  void sendDeadlineEmail_skipsWhenLawyerNotFound() {
    UUID lawyerId = UUID.randomUUID();
    when(userRepository.findById(lawyerId)).thenReturn(Optional.empty());

    service.sendDeadlineEmail(request(lawyerId));

    verify(resendEmailClient, never())
        .sendDeadlineEmail(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyString());
  }

  @Test
  void sendDeadlineEmail_skipsWhenUserIsNotLawyer() {
    UUID lawyerId = UUID.randomUUID();
    when(userRepository.findById(lawyerId))
        .thenReturn(Optional.of(user(UserRole.CLIENT, UserStatus.ACTIVE)));

    service.sendDeadlineEmail(request(lawyerId));

    verify(resendEmailClient, never())
        .sendDeadlineEmail(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyString());
  }

  @Test
  void sendDeadlineEmail_skipsWhenLawyerNotActive() {
    UUID lawyerId = UUID.randomUUID();
    when(userRepository.findById(lawyerId))
        .thenReturn(Optional.of(user(UserRole.LAWYER, UserStatus.REJECTED)));

    service.sendDeadlineEmail(request(lawyerId));

    verify(resendEmailClient, never())
        .sendDeadlineEmail(
            anyString(), anyString(), anyString(), anyString(), anyInt(), anyString());
  }

  @Test
  void sendDeadlineEmail_sendsWithCaseLinkBuiltFromFrontendBaseUrl() {
    UUID lawyerId = UUID.randomUUID();
    User lawyer = user(UserRole.LAWYER, UserStatus.ACTIVE);
    lawyer.setEmail("lawyer@pravoos.ru");
    when(userRepository.findById(lawyerId)).thenReturn(Optional.of(lawyer));
    UUID caseId = UUID.randomUUID();
    DeadlineEmailRequest req =
        new DeadlineEmailRequest(lawyerId, caseId, "Дело №1", "Подача иска", "2026-08-10", 3);

    service.sendDeadlineEmail(req);

    verify(resendEmailClient)
        .sendDeadlineEmail(
            eq("lawyer@pravoos.ru"),
            eq("Дело №1"),
            eq("Подача иска"),
            eq("2026-08-10"),
            eq(3),
            eq("https://app.pravoos.ru/cases/" + caseId));
  }

  @Test
  void sendDeadlineEmail_swallowsEmailClientException() {
    UUID lawyerId = UUID.randomUUID();
    User lawyer = user(UserRole.LAWYER, UserStatus.ACTIVE);
    lawyer.setEmail("lawyer@pravoos.ru");
    when(userRepository.findById(lawyerId)).thenReturn(Optional.of(lawyer));
    doThrow(new RuntimeException("resend down"))
        .when(resendEmailClient)
        .sendDeadlineEmail(any(), any(), any(), any(), anyInt(), any());

    service.sendDeadlineEmail(request(lawyerId));
  }

  private static DeadlineEmailRequest request(UUID lawyerId) {
    return new DeadlineEmailRequest(
        lawyerId, UUID.randomUUID(), "Дело", "Тип дедлайна", "2026-08-10", 1);
  }

  private static User user(UserRole role, UserStatus status) {
    User user = new User();
    user.setRole(role);
    user.setStatus(status);
    return user;
  }
}
