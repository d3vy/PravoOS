package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.mail.FetchedEmail;
import com.pravoos.ai.shared.mail.MailboxFetchResult;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MailSyncWriter {

  private static final Logger log = LoggerFactory.getLogger(MailSyncWriter.class);

  private final MailboxRepository mailboxRepository;
  private final EmailMessageWriter emailMessageWriter;
  private final MailboxAlertPublisher alertPublisher;

  MailSyncWriter(
      MailboxRepository mailboxRepository,
      EmailMessageWriter emailMessageWriter,
      MailboxAlertPublisher alertPublisher) {
    this.mailboxRepository = mailboxRepository;
    this.emailMessageWriter = emailMessageWriter;
    this.alertPublisher = alertPublisher;
  }

  @Transactional
  boolean markFailed(UUID mailboxId, String error) {
    Mailbox mailbox = requireMailbox(mailboxId);
    boolean pausedNow = mailbox.markFailed(error);
    LocalDateTime failedAt = LocalDateTime.now(ZoneOffset.UTC);
    mailbox.setLastSyncAt(failedAt);
    if (pausedNow) {
      alertPublisher.enqueuePaused(mailbox, failedAt);
    }
    return pausedNow;
  }

  int persistMessages(Mailbox mailbox, List<FetchedEmail> emails) {
    int saved = 0;
    for (FetchedEmail email : emails) {
      if (emailMessageWriter.saveIfAbsent(mailbox, email)) {
        saved++;
      }
    }
    return saved;
  }

  @Transactional
  void applyCursor(UUID mailboxId, MailboxFetchResult fetchResult) {
    Mailbox mailbox = requireMailbox(mailboxId);
    long lastSeenUid = fetchResult.lastSeenUid();
    if (!fetchResult.hasUnreadableUid()) {
      mailbox.clearUnreadableUid();
    } else if (mailbox.registerUnreadableUid(fetchResult.unreadableUid())) {
      lastSeenUid = fetchResult.unreadableUid();
      mailbox.clearUnreadableUid();
      log.error(
          "Письмо uid={} ящика {} не читается после нескольких попыток — пропускаю его",
          fetchResult.unreadableUid(),
          mailboxId);
    }
    mailbox.setUidValidity(fetchResult.uidValidity());
    mailbox.setLastSeenUid(lastSeenUid);
    mailbox.setLastSyncAt(LocalDateTime.now(ZoneOffset.UTC));
    mailbox.markConnected();
  }

  private Mailbox requireMailbox(UUID mailboxId) {
    return mailboxRepository
        .findById(mailboxId)
        .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
  }
}
