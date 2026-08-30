package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.CreateMailboxRequest;
import com.pravoos.ai.practice.internal.dto.MailboxResponse;
import com.pravoos.ai.practice.internal.dto.MailboxTestResult;
import com.pravoos.ai.practice.internal.dto.UpdateMailboxRequest;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.config.MailboxProperties;
import com.pravoos.ai.shared.exception.MailboxAlreadyExistsException;
import com.pravoos.ai.shared.exception.MailboxConnectionException;
import com.pravoos.ai.shared.exception.MailboxHostRequiredException;
import com.pravoos.ai.shared.exception.MailboxLimitExceededException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.mail.MailHostGuard;
import com.pravoos.ai.shared.mail.MailboxCredentials;
import com.pravoos.ai.shared.mail.MailboxReader;
import com.pravoos.ai.shared.model.enums.MailboxStatus;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailboxServiceTest {

  @Mock private MailboxRepository mailboxRepository;
  @Mock private MailboxReader mailboxReader;
  @Mock private MailHostGuard hostGuard;
  @Mock private RecycleBin recycleBin;

  private MailboxService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID mailboxId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new MailboxService(
            mailboxRepository,
            mailboxReader,
            new MailboxProperties(Duration.ofSeconds(5), Duration.ofSeconds(10), 2),
            hostGuard,
            recycleBin,
            selfProxy());
  }

  private MailboxService selfProxy() {
    return new MailboxService(
        mailboxRepository,
        mailboxReader,
        new MailboxProperties(Duration.ofSeconds(5), Duration.ofSeconds(10), 2),
        hostGuard,
        recycleBin,
        null);
  }

  @Test
  void createResolvesHostFromPresetAndNormalizesEmail() {
    when(mailboxRepository.save(any(Mailbox.class))).thenAnswer(call -> call.getArgument(0));

    MailboxResponse response =
        service.create(
            new CreateMailboxRequest("  Lawyer@Yandex.RU ", "app-password", null, null, null, null),
            userId);

    ArgumentCaptor<Mailbox> saved = ArgumentCaptor.forClass(Mailbox.class);
    verify(mailboxRepository).save(saved.capture());
    assertThat(saved.getValue().getEmailAddress()).isEqualTo("lawyer@yandex.ru");
    assertThat(saved.getValue().getImapHost()).isEqualTo("imap.yandex.ru");
    assertThat(saved.getValue().getImapPort()).isEqualTo(993);
    assertThat(saved.getValue().isImapSsl()).isTrue();
    assertThat(saved.getValue().getFolder()).isEqualTo("INBOX");
    assertThat(saved.getValue().getPassword()).isEqualTo("app-password");
    assertThat(response.status()).isEqualTo(MailboxStatus.PENDING);
  }

  @Test
  void createRejectsUnknownDomainWithoutExplicitHost() {
    assertThatThrownBy(
            () ->
                service.create(
                    new CreateMailboxRequest(
                        "lawyer@corp-legal.ru", "secret", null, null, null, null),
                    userId))
        .isInstanceOf(MailboxHostRequiredException.class);
    verify(mailboxRepository, never()).save(any());
  }

  @Test
  void createAcceptsExplicitHostForUnknownDomain() {
    when(mailboxRepository.save(any(Mailbox.class))).thenAnswer(call -> call.getArgument(0));

    service.create(
        new CreateMailboxRequest(
            "lawyer@corp-legal.ru", "secret", "imap.corp-legal.ru", 143, false, "Входящие"),
        userId);

    ArgumentCaptor<Mailbox> saved = ArgumentCaptor.forClass(Mailbox.class);
    verify(mailboxRepository).save(saved.capture());
    assertThat(saved.getValue().getImapHost()).isEqualTo("imap.corp-legal.ru");
    assertThat(saved.getValue().getImapPort()).isEqualTo(143);
    assertThat(saved.getValue().isImapSsl()).isFalse();
    assertThat(saved.getValue().getFolder()).isEqualTo("Входящие");
  }

  @Test
  void createRejectsDuplicateAddress() {
    when(mailboxRepository.existsByUserIdAndEmailAddress(userId, "lawyer@mail.ru"))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateMailboxRequest("Lawyer@mail.ru", "secret", null, null, null, null),
                    userId))
        .isInstanceOf(MailboxAlreadyExistsException.class);
  }

  @Test
  void createRejectsWhenLimitReached() {
    when(mailboxRepository.countByUserId(userId)).thenReturn(2L);

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateMailboxRequest("lawyer@gmail.com", "secret", null, null, null, null),
                    userId))
        .isInstanceOf(MailboxLimitExceededException.class);
  }

  @Test
  void updateResetsVerificationWhenPasswordChanges() {
    Mailbox mailbox = mailbox();
    mailbox.markConnected();
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.of(mailbox));
    when(mailboxRepository.save(any(Mailbox.class))).thenAnswer(call -> call.getArgument(0));

    MailboxResponse response =
        service.update(
            mailboxId,
            new UpdateMailboxRequest("new-password", null, null, null, null, null),
            userId);

    assertThat(mailbox.getPassword()).isEqualTo("new-password");
    assertThat(response.status()).isEqualTo(MailboxStatus.PENDING);
  }

  @Test
  void updateResumesAutoPausedMailboxWhenCredentialsAreFixed() {
    Mailbox mailbox = mailbox();
    for (int attempt = 0; attempt < 8; attempt++) {
      mailbox.markFailed("Неверный логин или пароль приложения");
    }
    assertThat(mailbox.isSyncEnabled()).isFalse();
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.of(mailbox));
    when(mailboxRepository.save(any(Mailbox.class))).thenAnswer(call -> call.getArgument(0));

    MailboxResponse response =
        service.update(
            mailboxId,
            new UpdateMailboxRequest("new-password", null, null, null, null, null),
            userId);

    assertThat(response.syncEnabled()).isTrue();
    assertThat(mailbox.getConsecutiveFailures()).isZero();
    assertThat(response.status()).isEqualTo(MailboxStatus.PENDING);
  }

  @Test
  void updateKeepsMailboxDisabledWhenOwnerAsksForIt() {
    Mailbox mailbox = mailbox();
    for (int attempt = 0; attempt < 8; attempt++) {
      mailbox.markFailed("Неверный логин или пароль приложения");
    }
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.of(mailbox));
    when(mailboxRepository.save(any(Mailbox.class))).thenAnswer(call -> call.getArgument(0));

    MailboxResponse response =
        service.update(
            mailboxId,
            new UpdateMailboxRequest("new-password", null, null, null, null, false),
            userId);

    assertThat(response.syncEnabled()).isFalse();
  }

  @Test
  void updateKeepsVerificationWhenOnlySyncToggled() {
    Mailbox mailbox = mailbox();
    mailbox.markConnected();
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.of(mailbox));
    when(mailboxRepository.save(any(Mailbox.class))).thenAnswer(call -> call.getArgument(0));

    MailboxResponse response =
        service.update(
            mailboxId, new UpdateMailboxRequest(null, null, null, null, null, false), userId);

    assertThat(response.syncEnabled()).isFalse();
    assertThat(response.status()).isEqualTo(MailboxStatus.OK);
  }

  @Test
  void testConnectionMarksMailboxConnected() {
    Mailbox mailbox = mailbox();
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.of(mailbox));

    MailboxTestResult result = service.testConnection(mailboxId, userId);

    ArgumentCaptor<MailboxCredentials> credentials =
        ArgumentCaptor.forClass(MailboxCredentials.class);
    verify(mailboxReader).verifyConnection(credentials.capture());
    assertThat(credentials.getValue().username()).isEqualTo("lawyer@yandex.ru");
    assertThat(credentials.getValue().password()).isEqualTo("app-password");
    assertThat(result.success()).isTrue();
    assertThat(mailbox.getStatus()).isEqualTo(MailboxStatus.OK);
    assertThat(mailbox.getLastError()).isNull();
  }

  @Test
  void testConnectionStoresErrorWithoutFailingRequest() {
    Mailbox mailbox = mailbox();
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.of(mailbox));
    doThrow(new MailboxConnectionException("Неверный логин или пароль приложения", null))
        .when(mailboxReader)
        .verifyConnection(any());

    MailboxTestResult result = service.testConnection(mailboxId, userId);

    assertThat(result.success()).isFalse();
    assertThat(result.message()).isEqualTo("Неверный логин или пароль приложения");
    assertThat(mailbox.getStatus()).isEqualTo(MailboxStatus.ERROR);
    assertThat(mailbox.getLastError()).isEqualTo("Неверный логин или пароль приложения");
  }

  @Test
  void foreignMailboxIsNotFound() {
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(mailboxId, userId))
        .isInstanceOf(MailboxNotFoundException.class);
  }

  @Test
  void deleteMovesOwnedMailboxToRecycleBin() {
    Mailbox mailbox = mailbox();
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.of(mailbox));
    DeletionActor actor = new DeletionActor(userId, DeletionRole.LAWYER, null, List.of());

    service.delete(mailboxId, actor);

    verify(recycleBin).moveToBin(RecycleBinEntityType.MAILBOX, mailboxId.toString(), actor);
  }

  @Test
  void deleteRejectsForeignMailbox() {
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.empty());
    DeletionActor actor = new DeletionActor(userId, DeletionRole.LAWYER, null, List.of());

    assertThatThrownBy(() -> service.delete(mailboxId, actor))
        .isInstanceOf(MailboxNotFoundException.class);
    verify(recycleBin, never()).moveToBin(any(), any(), any());
  }

  private Mailbox mailbox() {
    Mailbox mailbox = new Mailbox();
    mailbox.setUserId(userId);
    mailbox.setEmailAddress("lawyer@yandex.ru");
    mailbox.setImapHost("imap.yandex.ru");
    mailbox.setImapPort(993);
    mailbox.setImapSsl(true);
    mailbox.setPassword("app-password");
    mailbox.setFolder("INBOX");
    return mailbox;
  }
}
