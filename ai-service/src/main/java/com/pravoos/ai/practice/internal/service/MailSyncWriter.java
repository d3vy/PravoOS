package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.mail.FetchedEmail;
import com.pravoos.ai.shared.mail.MailboxFetchResult;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MailSyncWriter {

  private static final int MAX_MESSAGE_ID_LENGTH = 500;
  private static final String ADDRESS_SEPARATOR = ", ";

  private final MailboxRepository mailboxRepository;
  private final EmailMessageRepository emailMessageRepository;

  MailSyncWriter(
      MailboxRepository mailboxRepository, EmailMessageRepository emailMessageRepository) {
    this.mailboxRepository = mailboxRepository;
    this.emailMessageRepository = emailMessageRepository;
  }

  @Transactional
  void markFailed(Mailbox mailbox, String error) {
    mailbox.markFailed(error);
    mailbox.setLastSyncAt(LocalDateTime.now(ZoneOffset.UTC));
    mailboxRepository.save(mailbox);
  }

  @Transactional
  int applyFetch(Mailbox mailbox, MailboxFetchResult fetchResult) {
    int saved = persist(mailbox, fetchResult.messages());
    mailbox.setUidValidity(fetchResult.uidValidity());
    mailbox.setLastSeenUid(fetchResult.lastSeenUid());
    mailbox.setLastSyncAt(LocalDateTime.now(ZoneOffset.UTC));
    mailbox.markConnected();
    mailboxRepository.save(mailbox);
    return saved;
  }

  private int persist(Mailbox mailbox, List<FetchedEmail> emails) {
    if (emails.isEmpty()) {
      return 0;
    }
    List<String> messageIds = emails.stream().map(this::messageId).toList();
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
}
