package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.practice.internal.dto.EmailAttachmentImportResult;
import com.pravoos.ai.practice.internal.dto.EmailAttachmentResponse;
import com.pravoos.ai.practice.internal.model.entity.EmailAttachment;
import com.pravoos.ai.practice.internal.repository.jpa.EmailAttachmentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.service.EmailAttachmentImportWriter.ImportPlan;
import com.pravoos.ai.shared.exception.DocumentProcessingException;
import com.pravoos.ai.shared.exception.EmailAttachmentImportInProgressException;
import com.pravoos.ai.shared.exception.EmailMessageNotFoundException;
import com.pravoos.ai.shared.exception.FileTooLargeException;
import com.pravoos.ai.shared.exception.MalwareDetectedException;
import com.pravoos.ai.shared.exception.StorageQuotaExceededException;
import com.pravoos.ai.shared.exception.UnsafeFileContentException;
import com.pravoos.ai.shared.mail.FetchedAttachment;
import com.pravoos.ai.shared.mail.MailboxReader;
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
  private final EmailAttachmentImportWriter importWriter;
  private final EmailAttachmentImportLock importLock;
  private final MailboxReader mailboxReader;
  private final DocumentCommand documentCommand;

  public EmailAttachmentImportService(
      EmailMessageRepository emailMessageRepository,
      EmailAttachmentRepository emailAttachmentRepository,
      EmailAttachmentImportWriter importWriter,
      EmailAttachmentImportLock importLock,
      MailboxReader mailboxReader,
      DocumentCommand documentCommand) {
    this.emailMessageRepository = emailMessageRepository;
    this.emailAttachmentRepository = emailAttachmentRepository;
    this.importWriter = importWriter;
    this.importLock = importLock;
    this.mailboxReader = mailboxReader;
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
    String lockToken = importLock.acquire(emailId);
    if (lockToken == null) {
      log.info("Импорт вложений письма {} уже выполняется", emailId);
      throw new EmailAttachmentImportInProgressException(emailId);
    }
    try {
      return importUnderLock(emailId, lawyerId);
    } finally {
      importLock.release(emailId, lockToken);
    }
  }

  private EmailAttachmentImportResult importUnderLock(UUID emailId, UUID lawyerId) {
    ImportPlan plan = importWriter.loadPlan(emailId, lawyerId);
    if (!plan.fetchRequired()) {
      return toResult(plan, plan.known());
    }

    List<FetchedAttachment> fetched =
        mailboxReader.fetchAttachments(plan.credentials(), plan.cursor(), plan.imapUid());

    Map<Integer, EmailAttachment> byPartIndex = new HashMap<>();
    plan.known().forEach(attachment -> byPartIndex.put(attachment.getPartIndex(), attachment));

    List<EmailAttachment> processed = new ArrayList<>(fetched.size());
    boolean quotaExhausted = false;
    for (FetchedAttachment attachment : fetched) {
      EmailAttachment row = rowFor(plan.emailId(), attachment, byPartIndex);
      if (row.isImported()) {
        processed.add(row);
        continue;
      }
      if (attachment.isSkipped()) {
        row.markSkipped(attachment.skipReason().message());
      } else if (quotaExhausted) {
        row.markSkipped(QUOTA_EXHAUSTED_REASON);
      } else {
        quotaExhausted = store(row, attachment, plan, lawyerId);
      }
      processed.add(importWriter.save(row));
    }

    log.info(
        "Импорт вложений письма {} в дело {}: обработано {}",
        emailId,
        plan.caseId(),
        processed.size());
    return toResult(plan, processed);
  }

  private boolean store(
      EmailAttachment row, FetchedAttachment attachment, ImportPlan plan, UUID lawyerId) {
    try {
      DocumentUploadResponse uploaded =
          documentCommand.upload(
              multipartOf(attachment),
              titleOf(plan.subject(), attachment),
              lawyerId,
              plan.caseId());
      row.markImported(uploaded.id());
      return false;
    } catch (StorageQuotaExceededException e) {
      row.markSkipped(QUOTA_EXHAUSTED_REASON);
      log.warn("Импорт вложений письма {} прерван: {}", plan.emailId(), e.getMessage());
      return true;
    } catch (UnsafeFileContentException | MalwareDetectedException | FileTooLargeException e) {
      row.markRejected(e.getMessage());
      log.warn(
          "Вложение '{}' письма {} отклонено: {}",
          attachment.fileName(),
          plan.emailId(),
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
          plan.emailId(),
          e.toString());
      return false;
    }
  }

  private EmailAttachment rowFor(
      UUID emailId, FetchedAttachment attachment, Map<Integer, EmailAttachment> known) {
    EmailAttachment row =
        known.computeIfAbsent(
            attachment.partIndex(),
            partIndex ->
                new EmailAttachment(
                    emailId, partIndex, attachment.fileName(), attachment.contentType()));
    row.setFileName(attachment.fileName());
    row.setContentType(attachment.contentType());
    row.setSizeBytes(attachment.sizeBytes());
    return row;
  }

  private InMemoryMultipartFile multipartOf(FetchedAttachment attachment) {
    return new InMemoryMultipartFile(
        MULTIPART_NAME, attachment.fileName(), attachment.contentType(), attachment.content());
  }

  private String titleOf(String subject, FetchedAttachment attachment) {
    String title =
        subject == null || subject.isBlank()
            ? attachment.fileName()
            : attachment.fileName() + " — " + subject.trim();
    return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
  }

  private EmailAttachmentImportResult toResult(ImportPlan plan, List<EmailAttachment> attachments) {
    return EmailAttachmentImportResult.of(
        plan.emailId(),
        plan.caseId(),
        attachments.stream().map(EmailAttachmentResponse::from).toList());
  }

  private void requireOwnedMessage(UUID emailId, UUID lawyerId) {
    emailMessageRepository
        .findByIdAndUserId(emailId, lawyerId)
        .orElseThrow(() -> new EmailMessageNotFoundException(emailId));
  }
}
