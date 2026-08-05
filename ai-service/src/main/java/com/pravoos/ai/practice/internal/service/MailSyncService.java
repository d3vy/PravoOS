package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.MailSyncResult;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.config.MailSyncProperties;
import com.pravoos.ai.shared.exception.MailboxConnectionException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.mail.FetchedEmail;
import com.pravoos.ai.shared.mail.MailboxCredentials;
import com.pravoos.ai.shared.mail.MailboxFetchResult;
import com.pravoos.ai.shared.mail.MailboxReader;
import com.pravoos.ai.shared.mail.MailboxSyncCursor;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailSyncService {

  private static final Logger log = LoggerFactory.getLogger(MailSyncService.class);
  private static final int MAX_MESSAGE_ID_LENGTH = 500;
  private static final String ADDRESS_SEPARATOR = ", ";

  private final MailboxRepository mailboxRepository;
  private final EmailMessageRepository emailMessageRepository;
  private final MailboxReader mailboxReader;
  private final MailSyncProperties syncProperties;
  private final EmailLinkingService emailLinkingService;

  public MailSyncService(
      MailboxRepository mailboxRepository,
      EmailMessageRepository emailMessageRepository,
      MailboxReader mailboxReader,
      MailSyncProperties syncProperties,
      EmailLinkingService emailLinkingService) {
    this.mailboxRepository = mailboxRepository;
    this.emailMessageRepository = emailMessageRepository;
    this.mailboxReader = mailboxReader;
    this.syncProperties = syncProperties;
    this.emailLinkingService = emailLinkingService;
  }

  @Transactional
  public MailSyncResult syncMailbox(UUID mailboxId) {
    Mailbox mailbox =
        mailboxRepository
            .findById(mailboxId)
            .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
    return sync(mailbox);
  }

  @Transactional
  public MailSyncResult syncMailboxForUser(UUID mailboxId, UUID userId) {
    Mailbox mailbox =
        mailboxRepository
            .findByIdAndUserId(mailboxId, userId)
            .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
    return sync(mailbox);
  }

  private MailSyncResult sync(Mailbox mailbox) {
    MailboxFetchResult fetchResult;
    try {
      fetchResult =
          mailboxReader.fetchMessages(
              credentialsOf(mailbox), cursorOf(mailbox), syncProperties.maxMessagesPerRun());
    } catch (MailboxConnectionException e) {
      mailbox.markFailed(e.getMessage());
      mailbox.setLastSyncAt(LocalDateTime.now(ZoneOffset.UTC));
      mailboxRepository.save(mailbox);
      log.warn("Синк ящика {} не удался: {}", mailbox.getId(), e.getMessage());
      return MailSyncResult.failed(mailbox.getId(), e.getMessage());
    }

    if (fetchResult.reindexed()) {
      log.info(
          "Ящик {}: UIDVALIDITY изменился на {}, переиндексация с начала папки",
          mailbox.getId(),
          fetchResult.uidValidity());
    }

    int saved = persist(mailbox, fetchResult.messages());
    autoLink(mailbox, saved);
    mailbox.setUidValidity(fetchResult.uidValidity());
    mailbox.setLastSeenUid(fetchResult.lastSeenUid());
    mailbox.setLastSyncAt(LocalDateTime.now(ZoneOffset.UTC));
    mailbox.markConnected();
    mailboxRepository.save(mailbox);

    log.info(
        "Синк ящика {}: получено {}, сохранено {}, lastSeenUid={}",
        mailbox.getId(),
        fetchResult.messages().size(),
        saved,
        fetchResult.lastSeenUid());
    return MailSyncResult.ok(
        mailbox.getId(), fetchResult.messages().size(), saved, fetchResult.reindexed());
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

  private int persist(Mailbox mailbox, List<FetchedEmail> emails) {
    if (emails.isEmpty()) {
      return 0;
    }
    List<String> messageIds = emails.stream().map(email -> messageId(email)).toList();
    Set<String> known =
        new HashSet<>(emailMessageRepository.findExistingMessageIds(mailbox.getId(), messageIds));

    List<EmailMessage> newMessages = new ArrayList<>();
    for (FetchedEmail email : emails) {
      if (!known.add(messageId(email))) {
        continue;
      }
      newMessages.add(toEntity(mailbox, email));
    }
    if (newMessages.isEmpty()) {
      return 0;
    }
    emailMessageRepository.saveAll(newMessages);
    return newMessages.size();
  }

  private EmailMessage toEntity(Mailbox mailbox, FetchedEmail email) {
    EmailMessage message =
        new EmailMessage(
            mailbox.getId(), messageId(email), email.uid(), directionOf(mailbox, email));
    message.setThreadKey(threadKeyOf(email));
    message.setFromAddress(email.fromAddress());
    message.setToAddresses(joinAddresses(email.toAddresses()));
    message.setCcAddresses(joinAddresses(email.ccAddresses()));
    message.setSubject(email.subject());
    message.setBodyText(email.bodyText());
    message.setSentAt(email.sentAt());
    message.setHasAttachments(email.hasAttachments());
    message.setAttachmentCount(email.attachmentCount());
    return message;
  }

  private EmailDirection directionOf(Mailbox mailbox, FetchedEmail email) {
    String from = email.fromAddress();
    if (from != null && from.equalsIgnoreCase(mailbox.getEmailAddress())) {
      return EmailDirection.OUT;
    }
    return EmailDirection.IN;
  }

  private String threadKeyOf(FetchedEmail email) {
    if (!email.references().isEmpty()) {
      return truncate(email.references().getFirst());
    }
    if (email.inReplyTo() != null && !email.inReplyTo().isBlank()) {
      return truncate(email.inReplyTo());
    }
    return messageId(email);
  }

  private String messageId(FetchedEmail email) {
    return truncate(email.messageId());
  }

  private String truncate(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= MAX_MESSAGE_ID_LENGTH
        ? trimmed
        : trimmed.substring(0, MAX_MESSAGE_ID_LENGTH);
  }

  private String joinAddresses(List<String> addresses) {
    if (addresses.isEmpty()) {
      return null;
    }
    return String.join(
        ADDRESS_SEPARATOR,
        addresses.stream().map(address -> address.toLowerCase(Locale.ROOT)).toList());
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
