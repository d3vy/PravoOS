package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.practice.internal.dto.EmailAttachmentImportResult;
import com.pravoos.ai.practice.internal.dto.EmailAttachmentResponse;
import com.pravoos.ai.practice.internal.model.entity.EmailAttachment;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.EmailAttachmentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import com.pravoos.ai.shared.exception.EmailMessageNotFoundException;
import com.pravoos.ai.shared.exception.EmailNotLinkedToCaseException;
import com.pravoos.ai.shared.exception.FileTooLargeException;
import com.pravoos.ai.shared.exception.MailboxNotFoundException;
import com.pravoos.ai.shared.exception.MalwareDetectedException;
import com.pravoos.ai.shared.exception.StorageQuotaExceededException;
import com.pravoos.ai.shared.exception.UnsafeFileContentException;
import com.pravoos.ai.shared.mail.FetchedAttachment;
import com.pravoos.ai.shared.mail.MailboxCredentials;
import com.pravoos.ai.shared.mail.MailboxReader;
import com.pravoos.ai.shared.mail.MailboxSyncCursor;
import com.pravoos.ai.shared.util.InMemoryMultipartFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailAttachmentImportService {

  private static final Logger log = LoggerFactory.getLogger(EmailAttachmentImportService.class);
  private static final String MULTIPART_NAME = "file";
  private static final String QUOTA_EXHAUSTED_REASON =
      "Квота хранилища исчерпана — вложение не сохранено";
  private static final int MAX_TITLE_LENGTH = 255;

  private final EmailMessageRepository emailMessageRepository;
  private final EmailAttachmentRepository emailAttachmentRepository;
  private final MailboxRepository mailboxRepository;
  private final MailboxReader mailboxReader;
  private final CaseService caseService;
  private final DocumentCommand documentCommand;

  public EmailAttachmentImportService(
      EmailMessageRepository emailMessageRepository,
      EmailAttachmentRepository emailAttachmentRepository,
      MailboxRepository mailboxRepository,
      MailboxReader mailboxReader,
      CaseService caseService,
      DocumentCommand documentCommand) {
    this.emailMessageRepository = emailMessageRepository;
    this.emailAttachmentRepository = emailAttachmentRepository;
    this.mailboxRepository = mailboxRepository;
    this.mailboxReader = mailboxReader;
    this.caseService = caseService;
    this.documentCommand = documentCommand;
  }

  @Transactional(readOnly = true)
  public List<EmailAttachmentResponse> findByEmail(UUID emailId, UUID lawyerId) {
    requireOwnedMessage(emailId, lawyerId);
    return emailAttachmentRepository.findByEmailMessageIdOrderByPartIndexAsc(emailId).stream()
        .map(EmailAttachmentResponse::from)
        .toList();
  }

  public EmailAttachmentImportResult importAttachments(UUID emailId, UUID lawyerId) {
    EmailMessage message = requireOwnedMessage(emailId, lawyerId);
    UUID caseId = message.getCaseId();
    if (caseId == null) {
      throw new EmailNotLinkedToCaseException(emailId);
    }
    caseService.requireOwnedCase(caseId, lawyerId);

    List<EmailAttachment> known =
        emailAttachmentRepository.findByEmailMessageIdOrderByPartIndexAsc(emailId);
    if (!message.isHasAttachments() || allImported(known, message.getAttachmentCount())) {
      return toResult(message, known);
    }

    Mailbox mailbox =
        mailboxRepository
            .findById(message.getMailboxId())
            .orElseThrow(() -> new MailboxNotFoundException(message.getMailboxId()));
    List<FetchedAttachment> fetched =
        mailboxReader.fetchAttachments(
            credentialsOf(mailbox), cursorOf(mailbox), message.getImapUid());

    Map<Integer, EmailAttachment> byPartIndex = new HashMap<>();
    known.forEach(attachment -> byPartIndex.put(attachment.getPartIndex(), attachment));

    List<EmailAttachment> processed = new ArrayList<>(fetched.size());
    boolean quotaExhausted = false;
    for (FetchedAttachment attachment : fetched) {
      EmailAttachment row = rowFor(message, attachment, byPartIndex);
      if (row.isImported()) {
        processed.add(row);
        continue;
      }
      if (attachment.isSkipped()) {
        row.markSkipped(attachment.skipReason().message());
      } else if (quotaExhausted) {
        row.markSkipped(QUOTA_EXHAUSTED_REASON);
      } else {
        quotaExhausted = store(row, attachment, message, caseId, lawyerId);
      }
      processed.add(emailAttachmentRepository.save(row));
    }

    log.info(
        "Импорт вложений письма {} в дело {}: обработано {}", emailId, caseId, processed.size());
    return toResult(message, processed);
  }

  private boolean store(
      EmailAttachment row,
      FetchedAttachment attachment,
      EmailMessage message,
      UUID caseId,
      UUID lawyerId) {
    try {
      DocumentUploadResponse uploaded =
          documentCommand.upload(
              multipartOf(attachment), titleOf(message, attachment), lawyerId, caseId);
      row.markImported(uploaded.id());
      return false;
    } catch (StorageQuotaExceededException e) {
      row.markSkipped(QUOTA_EXHAUSTED_REASON);
      log.warn("Импорт вложений письма {} прерван: {}", message.getId(), e.getMessage());
      return true;
    } catch (UnsafeFileContentException | MalwareDetectedException | FileTooLargeException e) {
      row.markRejected(e.getMessage());
      log.warn(
          "Вложение '{}' письма {} отклонено: {}",
          attachment.fileName(),
          message.getId(),
          e.getMessage());
      return false;
    } catch (DocumentProcessingException e) {
      row.markSkipped(e.getMessage());
      return false;
    } catch (RuntimeException e) {
      row.markFailed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      log.warn(
          "Вложение '{}' письма {} не сохранено: {}",
          attachment.fileName(),
          message.getId(),
          e.toString());
      return false;
    }
  }

  private EmailAttachment rowFor(
      EmailMessage message, FetchedAttachment attachment, Map<Integer, EmailAttachment> known) {
    EmailAttachment row =
        known.computeIfAbsent(
            attachment.partIndex(),
            partIndex ->
                new EmailAttachment(
                    message.getId(), partIndex, attachment.fileName(), attachment.contentType()));
    row.setFileName(attachment.fileName());
    row.setContentType(attachment.contentType());
    row.setSizeBytes(attachment.sizeBytes());
    return row;
  }

  private boolean allImported(List<EmailAttachment> known, int attachmentCount) {
    return !known.isEmpty()
        && known.size() >= attachmentCount
        && known.stream().allMatch(EmailAttachment::isImported);
  }

  private InMemoryMultipartFile multipartOf(FetchedAttachment attachment) {
    return new InMemoryMultipartFile(
        MULTIPART_NAME, attachment.fileName(), attachment.contentType(), attachment.content());
  }

  private String titleOf(EmailMessage message, FetchedAttachment attachment) {
    String subject = message.getSubject();
    String title =
        subject == null || subject.isBlank()
            ? attachment.fileName()
            : attachment.fileName() + " — " + subject.trim();
    return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
  }

  private EmailAttachmentImportResult toResult(
      EmailMessage message, List<EmailAttachment> attachments) {
    return EmailAttachmentImportResult.of(
        message.getId(),
        message.getCaseId(),
        attachments.stream().map(EmailAttachmentResponse::from).toList());
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

  private EmailMessage requireOwnedMessage(UUID emailId, UUID lawyerId) {
    return emailMessageRepository
        .findByIdAndUserId(emailId, lawyerId)
        .orElseThrow(() -> new EmailMessageNotFoundException(emailId));
  }
}
