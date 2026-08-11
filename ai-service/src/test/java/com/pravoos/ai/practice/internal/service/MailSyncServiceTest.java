package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.config.MailSyncProperties;
import com.pravoos.ai.shared.exception.MailboxConnectionException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.mail.FetchedEmail;
import com.pravoos.ai.shared.mail.MailboxFetchResult;
import com.pravoos.ai.shared.mail.MailboxReader;
import com.pravoos.ai.shared.mail.MailboxSyncCursor;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import com.pravoos.ai.shared.model.enums.MailboxStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailSyncServiceTest {

  @Mock private MailboxRepository mailboxRepository;
  @Mock private EmailMessageRepository emailMessageRepository;
  @Mock private MailboxReader mailboxReader;
  @Mock private EmailLinkingService emailLinkingService;
  @Mock private MailboxSyncLock syncLock;
  @Mock private MailboxAlertPublisher alertPublisher;

  private MailSyncService service;

  private final UUID mailboxId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private Mailbox mailbox;

  @BeforeEach
  void setUp() {
    service =
        new MailSyncService(
            mailboxRepository,
            mailboxReader,
            new MailSyncProperties(50, 1000, 500),
            emailLinkingService,
            new MailSyncWriter(
                mailboxRepository, new EmailMessageWriter(emailMessageRepository), alertPublisher),
            syncLock);
    mailbox = new Mailbox();
    mailbox.setUserId(userId);
    mailbox.setEmailAddress("lawyer@pravoos.ru");
    mailbox.setImapHost("imap.yandex.ru");
    mailbox.setImapPort(993);
    mailbox.setPassword("app-password");
    mailbox.setFolder("INBOX");
    mailbox.setUidValidity(42L);
    mailbox.setLastSeenUid(100L);
    ReflectionTestUtils.setField(mailbox, "id", mailboxId);

    when(syncLock.acquire(mailboxId)).thenReturn("lock-token");
    when(mailboxRepository.findById(mailboxId)).thenReturn(Optional.of(mailbox));
    when(emailMessageRepository.existsByMailboxIdAndMessageId(any(), any())).thenReturn(false);
  }

  @Test
  void savesNewMessagesAndAdvancesCursor() {
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenReturn(
            new MailboxFetchResult(
                42L,
                102L,
                false,
                List.of(incoming("<a@example.com>", 101L), incoming("<b@example.com>", 102L))));

    MailSyncResult result = service.syncMailbox(mailboxId);

    assertThat(result.success()).isTrue();
    assertThat(result.fetched()).isEqualTo(2);
    assertThat(result.saved()).isEqualTo(2);
    assertThat(mailbox.getStatus()).isEqualTo(MailboxStatus.OK);
    assertThat(mailbox.getLastSeenUid()).isEqualTo(102L);
    assertThat(mailbox.getUidValidity()).isEqualTo(42L);
    assertThat(mailbox.getLastSyncAt()).isNotNull();

    ArgumentCaptor<MailboxSyncCursor> cursor = ArgumentCaptor.forClass(MailboxSyncCursor.class);
    verify(mailboxReader).fetchMessages(any(), cursor.capture(), eq(50));
    assertThat(cursor.getValue().startUid(42L)).isEqualTo(101L);
  }

  @Test
  void skipsMessagesAlreadyStored() {
    when(emailMessageRepository.existsByMailboxIdAndMessageId(mailboxId, "<a@example.com>"))
        .thenReturn(true);
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenReturn(
            new MailboxFetchResult(
                42L,
                102L,
                false,
                List.of(incoming("<a@example.com>", 101L), incoming("<b@example.com>", 102L))));

    MailSyncResult result = service.syncMailbox(mailboxId);

    assertThat(result.saved()).isEqualTo(1);
    assertThat(savedMessages())
        .extracting(EmailMessage::getMessageId)
        .containsExactly("<b@example.com>");
  }

  @Test
  void marksOutgoingWhenSenderIsMailboxOwner() {
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenReturn(
            new MailboxFetchResult(
                42L,
                101L,
                false,
                List.of(
                    new FetchedEmail(
                        "<out@example.com>",
                        101L,
                        "Ответ",
                        "LAWYER@pravoos.ru",
                        List.of("client@example.com"),
                        List.of(),
                        "Текст",
                        LocalDateTime.now(ZoneOffset.UTC),
                        0,
                        null,
                        List.of()))));

    service.syncMailbox(mailboxId);

    assertThat(savedMessages().getFirst().getDirection()).isEqualTo(EmailDirection.OUT);
  }

  @Test
  void usesFirstReferenceAsThreadKey() {
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenReturn(
            new MailboxFetchResult(
                42L,
                101L,
                false,
                List.of(
                    new FetchedEmail(
                        "<reply@example.com>",
                        101L,
                        "Re: Договор",
                        "client@example.com",
                        List.of("lawyer@pravoos.ru"),
                        List.of(),
                        "Текст",
                        LocalDateTime.now(ZoneOffset.UTC),
                        1,
                        "<second@example.com>",
                        List.of("<root@example.com>", "<second@example.com>")))));

    service.syncMailbox(mailboxId);

    EmailMessage saved = savedMessages().getFirst();
    assertThat(saved.getThreadKey()).isEqualTo("<root@example.com>");
    assertThat(saved.isHasAttachments()).isTrue();
    assertThat(saved.getAttachmentCount()).isEqualTo(1);
  }

  @Test
  void connectionFailureSwitchesMailboxToErrorWithoutSavingMessages() {
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenThrow(new MailboxConnectionException("Неверный логин или пароль приложения", null));

    MailSyncResult result = service.syncMailbox(mailboxId);

    assertThat(result.success()).isFalse();
    assertThat(result.error()).isEqualTo("Неверный логин или пароль приложения");
    assertThat(mailbox.getStatus()).isEqualTo(MailboxStatus.ERROR);
    assertThat(mailbox.getLastError()).isEqualTo("Неверный логин или пароль приложения");
    assertThat(mailbox.getLastSeenUid()).isEqualTo(100L);
    verify(emailMessageRepository, never()).saveAndFlush(any());
  }

  @Test
  void repeatedFailuresBackOffAndEventuallyPauseAutoSync() {
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenThrow(new MailboxConnectionException("Неверный логин или пароль приложения", null));

    service.syncMailbox(mailboxId);
    assertThat(mailbox.isSyncEnabled()).isTrue();
    assertThat(mailbox.isRetryPending(LocalDateTime.now(ZoneOffset.UTC))).isTrue();

    for (int attempt = 0; attempt < 7; attempt++) {
      service.syncMailbox(mailboxId);
    }

    assertThat(mailbox.isSyncEnabled()).isFalse();
    assertThat(mailbox.getStatus()).isEqualTo(MailboxStatus.ERROR);
    verify(alertPublisher).enqueuePaused(eq(mailbox), any(LocalDateTime.class));

    service.syncMailbox(mailboxId);

    verify(alertPublisher, times(1)).enqueuePaused(eq(mailbox), any(LocalDateTime.class));
  }

  @Test
  void unreadableMessageStopsCursorAndIsSkippedAfterRepeatedAttempts() {
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenReturn(new MailboxFetchResult(42L, 100L, false, List.of(), 101L));

    service.syncMailbox(mailboxId);
    assertThat(mailbox.getLastSeenUid()).isEqualTo(100L);

    service.syncMailbox(mailboxId);
    assertThat(mailbox.getLastSeenUid()).isEqualTo(100L);

    service.syncMailbox(mailboxId);
    assertThat(mailbox.getLastSeenUid()).isEqualTo(101L);
    assertThat(mailbox.getUnreadableUid()).isNull();
  }

  @Test
  void concurrentSyncIsSkippedWhileLockIsHeld() {
    when(syncLock.acquire(mailboxId)).thenReturn(null);

    MailSyncResult result = service.syncMailbox(mailboxId);

    assertThat(result.success()).isTrue();
    assertThat(result.saved()).isZero();
    verify(mailboxReader, never()).fetchMessages(any(), any(), anyInt());
  }

  @Test
  void reindexAfterUidValidityChangeUpdatesCursorToNewValidity() {
    when(emailMessageRepository.existsByMailboxIdAndMessageId(mailboxId, "<a@example.com>"))
        .thenReturn(true);
    when(mailboxReader.fetchMessages(any(), any(), anyInt()))
        .thenReturn(
            new MailboxFetchResult(77L, 2L, true, List.of(incoming("<a@example.com>", 2L))));

    MailSyncResult result = service.syncMailbox(mailboxId);

    assertThat(result.reindexed()).isTrue();
    assertThat(result.saved()).isZero();
    assertThat(mailbox.getUidValidity()).isEqualTo(77L);
    assertThat(mailbox.getLastSeenUid()).isEqualTo(2L);
  }

  @Test
  void foreignMailboxIsNotFoundForUser() {
    when(mailboxRepository.findByIdAndUserId(mailboxId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.syncMailboxForUser(mailboxId, userId))
        .isInstanceOf(MailboxNotFoundException.class);
    verify(mailboxReader, never()).fetchMessages(any(), any(), anyInt());
  }

  private FetchedEmail incoming(String messageId, long uid) {
    return new FetchedEmail(
        messageId,
        uid,
        "Тема",
        "client@example.com",
        List.of("lawyer@pravoos.ru"),
        List.of(),
        "Текст письма",
        LocalDateTime.now(ZoneOffset.UTC),
        0,
        null,
        List.of());
  }

  private List<EmailMessage> savedMessages() {
    ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
    verify(emailMessageRepository, org.mockito.Mockito.atLeastOnce())
        .saveAndFlush(captor.capture());
    return captor.getAllValues();
  }
}
