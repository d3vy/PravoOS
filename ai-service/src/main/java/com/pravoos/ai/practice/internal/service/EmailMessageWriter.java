package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.shared.mail.FetchedEmail;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class EmailMessageWriter {

  private static final Logger log = LoggerFactory.getLogger(EmailMessageWriter.class);
  private static final int MAX_MESSAGE_ID_LENGTH = 500;
  private static final String ADDRESS_SEPARATOR = ", ";

  private final EmailMessageRepository emailMessageRepository;

  EmailMessageWriter(EmailMessageRepository emailMessageRepository) {
    this.emailMessageRepository = emailMessageRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  boolean saveIfAbsent(Mailbox mailbox, FetchedEmail email) {
    String messageId = truncate(email.messageId());
    if (emailMessageRepository.existsByMailboxIdAndMessageId(mailbox.getId(), messageId)) {
      return false;
    }
    try {
      emailMessageRepository.saveAndFlush(toEntity(mailbox, email, messageId));
      return true;
    } catch (DataIntegrityViolationException e) {
      log.debug("Письмо {} ящика {} уже сохранено параллельно", messageId, mailbox.getId());
      return false;
    }
  }

  private EmailMessage toEntity(Mailbox mailbox, FetchedEmail email, String messageId) {
    EmailMessage message =
        new EmailMessage(mailbox.getId(), messageId, email.uid(), directionOf(mailbox, email));
    message.setThreadKey(threadKeyOf(email, messageId));
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

  private String threadKeyOf(FetchedEmail email, String messageId) {
    if (!email.references().isEmpty()) {
      return truncate(email.references().getFirst());
    }
    if (email.inReplyTo() != null && !email.inReplyTo().isBlank()) {
      return truncate(email.inReplyTo());
    }
    return messageId;
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
