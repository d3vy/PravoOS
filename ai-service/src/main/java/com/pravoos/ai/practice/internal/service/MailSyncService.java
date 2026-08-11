package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.config.MailSyncProperties;
import com.pravoos.ai.shared.exception.MailboxConnectionException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.mail.MailboxCredentials;
import com.pravoos.ai.shared.mail.MailboxFetchResult;
import com.pravoos.ai.shared.mail.MailboxReader;
import com.pravoos.ai.shared.mail.MailboxSyncCursor;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MailSyncService {

  private static final Logger log = LoggerFactory.getLogger(MailSyncService.class);

  private final MailboxRepository mailboxRepository;
  private final MailboxReader mailboxReader;
  private final MailSyncProperties syncProperties;
  private final EmailLinkingService emailLinkingService;
  private final MailSyncWriter mailSyncWriter;
  private final MailboxSyncLock syncLock;

  public MailSyncService(
      MailboxRepository mailboxRepository,
      MailboxReader mailboxReader,
      MailSyncProperties syncProperties,
      EmailLinkingService emailLinkingService,
      MailSyncWriter mailSyncWriter,
      MailboxSyncLock syncLock) {
    this.mailboxRepository = mailboxRepository;
    this.mailboxReader = mailboxReader;
    this.syncProperties = syncProperties;
    this.emailLinkingService = emailLinkingService;
    this.mailSyncWriter = mailSyncWriter;
    this.syncLock = syncLock;
  }

  public MailSyncResult syncMailbox(UUID mailboxId) {
    return sync(requireMailbox(mailboxId));
  }

  public MailSyncResult syncMailboxForUser(UUID mailboxId, UUID userId) {
    Mailbox mailbox =
        mailboxRepository
            .findByIdAndUserId(mailboxId, userId)
            .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
    return sync(mailbox);
  }

  private Mailbox requireMailbox(UUID mailboxId) {
    return mailboxRepository
        .findById(mailboxId)
        .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
  }

  private MailSyncResult sync(Mailbox mailbox) {
    String lockToken = syncLock.acquire(mailbox.getId());
    if (lockToken == null) {
      log.info("Синк ящика {} пропущен: уже выполняется", mailbox.getId());
      return MailSyncResult.alreadyRunning(mailbox.getId(), mailbox.getStatus());
    }
    try {
      return syncUnderLock(mailbox);
    } finally {
      syncLock.release(mailbox.getId(), lockToken);
    }
  }

  private MailSyncResult syncUnderLock(Mailbox mailbox) {
    MailboxFetchResult fetchResult;
    try {
      fetchResult =
          mailboxReader.fetchMessages(
              credentialsOf(mailbox), cursorOf(mailbox), syncProperties.maxMessagesPerRun());
    } catch (MailboxConnectionException e) {
      return failSync(mailbox, e.getMessage());
    }

    if (fetchResult.reindexed()) {
      log.info(
          "Ящик {}: UIDVALIDITY изменился на {}, переиндексация с начала папки",
          mailbox.getId(),
          fetchResult.uidValidity());
    }

    int saved = mailSyncWriter.persistMessages(mailbox, fetchResult.messages());
    mailSyncWriter.applyCursor(mailbox.getId(), fetchResult);
    autoLink(mailbox, saved);

    log.info(
        "Синк ящика {}: получено {}, сохранено {}, lastSeenUid={}",
        mailbox.getId(),
        fetchResult.messages().size(),
        saved,
        fetchResult.lastSeenUid());
    return MailSyncResult.ok(
        mailbox.getId(), fetchResult.messages().size(), saved, fetchResult.reindexed());
  }

  private MailSyncResult failSync(Mailbox mailbox, String error) {
    if (mailSyncWriter.markFailed(mailbox.getId(), error)) {
      log.error(
          "Автосинк ящика {} остановлен после серии ошибок, требуется вмешательство владельца: {}",
          mailbox.getId(),
          error);
    } else {
      log.warn("Синк ящика {} не удался: {}", mailbox.getId(), error);
    }
    return MailSyncResult.failed(mailbox.getId(), error);
  }

  private void autoLink(Mailbox mailbox, int saved) {
    if (saved == 0) {
      return;
    }
    try {
      emailLinkingService.autoLinkMailbox(mailbox.getId());
    } catch (RuntimeException e) {
      log.warn("Авто-привязка писем ящика {} не удалась: {}", mailbox.getId(), e.getMessage());
    }
  }

  private MailboxCredentials credentialsOf(Mailbox mailbox) {
    return new MailboxCredentials(
        mailbox.getImapHost(),
        mailbox.getImapPort(),
        mailbox.isImapSsl(),
        mailbox.getEmailAddress(),
        mailbox.getPassword(),
        mailbox.getFolder());
  }

  private MailboxSyncCursor cursorOf(Mailbox mailbox) {
    return new MailboxSyncCursor(mailbox.getUidValidity(), mailbox.getLastSeenUid());
  }
}
