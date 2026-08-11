package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.EmailAttachment;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.EmailAttachmentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.exception.EmailMessageNotFoundException;
import com.pravoos.ai.shared.exception.EmailNotLinkedToCaseException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.mail.MailboxCredentials;
import com.pravoos.ai.shared.mail.MailboxSyncCursor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EmailAttachmentImportWriter {

  private final EmailMessageRepository emailMessageRepository;
  private final EmailAttachmentRepository emailAttachmentRepository;
  private final MailboxRepository mailboxRepository;
  private final CaseService caseService;

  EmailAttachmentImportWriter(
      EmailMessageRepository emailMessageRepository,
      EmailAttachmentRepository emailAttachmentRepository,
      MailboxRepository mailboxRepository,
      CaseService caseService) {
    this.emailMessageRepository = emailMessageRepository;
    this.emailAttachmentRepository = emailAttachmentRepository;
    this.mailboxRepository = mailboxRepository;
    this.caseService = caseService;
  }

  @Transactional(readOnly = true)
  ImportPlan loadPlan(UUID emailId, UUID lawyerId) {
    EmailMessage message =
        emailMessageRepository
            .findByIdAndUserId(emailId, lawyerId)
            .orElseThrow(() -> new EmailMessageNotFoundException(emailId));
    UUID caseId = message.getCaseId();
    if (caseId == null) {
      throw new EmailNotLinkedToCaseException(emailId);
    }
    caseService.requireOwnedCase(caseId, lawyerId);

    List<EmailAttachment> known =
        emailAttachmentRepository.findByEmailMessageIdOrderByPartIndexAsc(emailId);
    if (!message.isHasAttachments() || allSettled(known, message.getAttachmentCount())) {
      return ImportPlan.nothingToDo(emailId, caseId, known);
    }

    Mailbox mailbox =
        mailboxRepository
            .findById(message.getMailboxId())
            .orElseThrow(() -> new MailboxNotFoundException(message.getMailboxId()));
    return new ImportPlan(
        emailId,
        caseId,
        message.getSubject(),
        message.getImapUid(),
        true,
        known,
        credentialsOf(mailbox),
        cursorOf(mailbox));
  }

  @Transactional
  EmailAttachment save(EmailAttachment row) {
    return emailAttachmentRepository.save(row);
  }

  private boolean allSettled(List<EmailAttachment> known, int attachmentCount) {
    return !known.isEmpty()
        && known.size() >= attachmentCount
        && known.stream().allMatch(EmailAttachment::isSettled);
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

  record ImportPlan(
      UUID emailId,
      UUID caseId,
      String subject,
      long imapUid,
      boolean fetchRequired,
      List<EmailAttachment> known,
      MailboxCredentials credentials,
      MailboxSyncCursor cursor) {

    static ImportPlan nothingToDo(UUID emailId, UUID caseId, List<EmailAttachment> known) {
      return new ImportPlan(emailId, caseId, null, 0L, false, known, null, null);
    }
  }
}
