package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pravoos.user.collaboration.internal.dto.CaseMessageNotificationRequest;
import com.pravoos.user.collaboration.internal.dto.CaseMessageNotificationResult;
import com.pravoos.user.collaboration.internal.repository.ClientPortalInviteRepository;
import com.pravoos.user.collaboration.internal.service.CaseMessageNotificationService;
import com.pravoos.user.collaboration.internal.service.TelegramLinkService;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.email.ResendEmailClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseMessageNotificationServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private ClientPortalInviteRepository clientPortalInviteRepository;
  @Mock private TelegramLinkService telegramLinkService;
  @Mock private ResendEmailClient resendEmailClient;
  @Mock private ResendProperties resendProperties;

  private CaseMessageNotificationService service;

  private final UUID caseId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();
  private final UUID clientUserId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new CaseMessageNotificationService(
            userRepository,
            clientPortalInviteRepository,
            telegramLinkService,
            resendEmailClient,
            resendProperties);
  }

  private User activeUser(UUID id, boolean email, boolean telegram) {
    User user = new User();
    user.setId(id);
    user.setEmail("user@example.com");
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    user.setCaseMessageEmail(email);
    user.setCaseMessageTelegram(telegram);
    return user;
  }

  @Test
  void dispatch_toLawyer_sendsEmailWithLawyerLink_andResolvesTelegram() {
    when(resendProperties.frontendBaseUrl()).thenReturn("https://app.pravoos.ru");
    when(userRepository.findById(lawyerId))
        .thenReturn(Optional.of(activeUser(lawyerId, true, true)));
    when(telegramLinkService.resolveChatId(lawyerId)).thenReturn(Optional.of(999L));

    CaseMessageNotificationResult result =
        service.dispatch(
            new CaseMessageNotificationRequest(caseId, "Дело", "CLIENT", lawyerId, null, "Вопрос"));

    assertThat(result.telegramChatId()).isEqualTo(999L);
    verify(resendEmailClient)
        .sendCaseMessageEmail(
            eq("user@example.com"),
            eq("Дело"),
            eq("Клиент"),
            eq("Вопрос"),
            eq("https://app.pravoos.ru/cases/" + caseId));
  }

  @Test
  void dispatch_toClient_resolvesUserViaInvite_usesPortalLink_noTelegramWhenOff() {
    when(resendProperties.frontendBaseUrl()).thenReturn("https://app.pravoos.ru");
    when(clientPortalInviteRepository.findAcceptedUserIdsByClientId(clientId))
        .thenReturn(List.of(clientUserId));
    when(userRepository.findById(clientUserId))
        .thenReturn(Optional.of(activeUser(clientUserId, true, false)));

    CaseMessageNotificationResult result =
        service.dispatch(
            new CaseMessageNotificationRequest(caseId, "Дело", "LAWYER", null, clientId, "Ответ"));

    assertThat(result.telegramChatId()).isNull();
    verify(resendEmailClient)
        .sendCaseMessageEmail(
            anyString(),
            eq("Дело"),
            eq("Ваш юрист"),
            eq("Ответ"),
            eq("https://app.pravoos.ru/portal/cases/" + caseId));
  }

  @Test
  void dispatch_emailDisabled_skipsEmail() {
    when(userRepository.findById(lawyerId))
        .thenReturn(Optional.of(activeUser(lawyerId, false, false)));

    CaseMessageNotificationResult result =
        service.dispatch(
            new CaseMessageNotificationRequest(caseId, "Дело", "CLIENT", lawyerId, null, "Вопрос"));

    assertThat(result.telegramChatId()).isNull();
    verify(resendEmailClient, never()).sendCaseMessageEmail(any(), any(), any(), any(), any());
  }

  @Test
  void dispatch_noAcceptedClientUser_returnsNone() {
    when(clientPortalInviteRepository.findAcceptedUserIdsByClientId(clientId))
        .thenReturn(List.of());

    CaseMessageNotificationResult result =
        service.dispatch(
            new CaseMessageNotificationRequest(caseId, "Дело", "LAWYER", null, clientId, "Ответ"));

    assertThat(result.telegramChatId()).isNull();
    verify(resendEmailClient, never()).sendCaseMessageEmail(any(), any(), any(), any(), any());
  }
}
