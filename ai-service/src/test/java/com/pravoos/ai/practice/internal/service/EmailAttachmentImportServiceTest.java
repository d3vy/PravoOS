package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.practice.internal.dto.EmailAttachmentImportResult;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.EmailAttachment;
import com.pravoos.ai.practice.internal.model.entity.EmailMessage;
import com.pravoos.ai.practice.internal.model.entity.Mailbox;
import com.pravoos.ai.practice.internal.repository.jpa.EmailAttachmentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.EmailMessageRepository;
import com.pravoos.ai.practice.internal.repository.jpa.MailboxRepository;
import com.pravoos.ai.shared.exception.EmailMessageNotFoundException;
import com.pravoos.ai.shared.exception.EmailNotLinkedToCaseException;
import com.pravoos.ai.shared.exception.MalwareDetectedException;
import com.pravoos.ai.shared.exception.StorageQuotaExceededException;
import com.pravoos.ai.shared.mail.AttachmentSkipReason;
import com.pravoos.ai.shared.mail.FetchedAttachment;
import com.pravoos.ai.shared.mail.MailboxReader;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.model.enums.EmailAttachmentStatus;
import com.pravoos.ai.shared.model.enums.EmailDirection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailAttachmentImportServiceTest {

  @Mock private EmailMessageRepository emailMessageRepository;
  @Mock private EmailAttachmentRepository emailAttachmentRepository;
  @Mock private MailboxRepository mailboxRepository;
  @Mock private MailboxReader mailboxReader;
  @Mock private CaseService caseService;
  @Mock private DocumentCommand documentCommand;

  private EmailAttachmentImportService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID mailboxId = UUID.randomUUID();
  private final UUID emailId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  private EmailMessage message;

  @BeforeEach
  void setUp() {
    service =
        new EmailAttachmentImportService(
            emailMessageRepository,
            emailAttachmentRepository,
            mailboxRepository,
            mailboxReader,
            caseService,
            documentCommand);

    message = new EmailMessage(mailboxId, "<msg-1@example.com>", 42L, EmailDirection.IN);
    ReflectionTestUtils.setField(message, "id", emailId);
    message.setSubject("Договор аренды");
    message.setHasAttachments(true);
    message.setAttachmentCount(1);
    message.linkToCase(caseId);

    Mailbox mailbox = new Mailbox();
    ReflectionTestUtils.setField(mailbox, "id", mailboxId);
    mailbox.setUserId(lawyerId);
    mailbox.setEmailAddress("lawyer@example.com");
    mailbox.setImapHost("imap.example.com");
    mailbox.setImapPort(993);
    mailbox.setImapSsl(true);
    mailbox.setPassword("secret");
    mailbox.setFolder("INBOX");
    mailbox.setUidValidity(7L);
    mailbox.setLastSeenUid(100L);

    when(emailMessageRepository.findByIdAndUserId(emailId, lawyerId))
        .thenReturn(Optional.of(message));
    when(mailboxRepository.findById(mailboxId)).thenReturn(Optional.of(mailbox));
    when(emailAttachmentRepository.findByEmailMessageIdOrderByPartIndexAsc(emailId))
        .thenReturn(List.of());
    when(emailAttachmentRepository.save(any(EmailAttachment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(caseService.requireOwnedCase(caseId, lawyerId)).thenReturn(new Case());
  }

  @Test
  void importsAttachmentAsCaseDocument() {
    when(mailboxReader.fetchAttachments(any(), any(), anyLong()))
        .thenReturn(List.of(loaded(0, "contract.pdf")));
    UUID documentId = UUID.randomUUID();
    when(documentCommand.upload(any(MultipartFile.class), any(), eq(lawyerId), eq(caseId)))
        .thenReturn(
            new DocumentUploadResponse(
                documentId, "contract", "contract.pdf", DocumentStatus.PROCESSING));

    EmailAttachmentImportResult result = service.importAttachments(emailId, lawyerId);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.attachments()).hasSize(1);
    assertThat(result.attachments().getFirst().status()).isEqualTo(EmailAttachmentStatus.IMPORTED);
    assertThat(result.attachments().getFirst().documentId()).isEqualTo(documentId);
  }

  @Test
  void rejectsInfectedAttachmentAndKeepsImportingTheRest() {
    message.setAttachmentCount(2);
    when(mailboxReader.fetchAttachments(any(), any(), anyLong()))
        .thenReturn(List.of(loaded(0, "virus.pdf"), loaded(1, "clean.pdf")));
    when(documentCommand.upload(any(MultipartFile.class), any(), eq(lawyerId), eq(caseId)))
        .thenThrow(new MalwareDetectedException("Eicar-Test-Signature"))
        .thenReturn(
            new DocumentUploadResponse(
                UUID.randomUUID(), "clean", "clean.pdf", DocumentStatus.PROCESSING));

    EmailAttachmentImportResult result = service.importAttachments(emailId, lawyerId);

    assertThat(result.rejected()).isEqualTo(1);
    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.attachments().getFirst().status()).isEqualTo(EmailAttachmentStatus.REJECTED);
    assertThat(result.attachments().getFirst().documentId()).isNull();
    assertThat(result.attachments().getFirst().reason()).contains("Eicar-Test-Signature");
  }

  @Test
  void stopsUploadingAfterStorageQuotaIsExhausted() {
    message.setAttachmentCount(2);
    when(mailboxReader.fetchAttachments(any(), any(), anyLong()))
        .thenReturn(List.of(loaded(0, "first.pdf"), loaded(1, "second.pdf")));
    when(documentCommand.upload(any(MultipartFile.class), any(), eq(lawyerId), eq(caseId)))
        .thenThrow(new StorageQuotaExceededException("Квота исчерпана"));

    EmailAttachmentImportResult result = service.importAttachments(emailId, lawyerId);

    assertThat(result.skipped()).isEqualTo(2);
    assertThat(result.imported()).isZero();
    verify(documentCommand).upload(any(MultipartFile.class), any(), eq(lawyerId), eq(caseId));
  }

  @Test
  void marksOversizedAttachmentSkippedWithoutUpload() {
    when(mailboxReader.fetchAttachments(any(), any(), anyLong()))
        .thenReturn(
            List.of(
                FetchedAttachment.skipped(
                    0,
                    "huge.pdf",
                    "application/pdf",
                    99_000_000L,
                    AttachmentSkipReason.TOO_LARGE)));

    EmailAttachmentImportResult result = service.importAttachments(emailId, lawyerId);

    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.attachments().getFirst().reason())
        .isEqualTo(AttachmentSkipReason.TOO_LARGE.message());
    verify(documentCommand, never()).upload(any(MultipartFile.class), any(), any(), any());
  }

  @Test
  void doesNotTouchMailboxWhenEverythingIsAlreadyImported() {
    EmailAttachment imported = new EmailAttachment(emailId, 0, "contract.pdf", "application/pdf");
    imported.markImported(UUID.randomUUID());
    when(emailAttachmentRepository.findByEmailMessageIdOrderByPartIndexAsc(emailId))
        .thenReturn(List.of(imported));

    EmailAttachmentImportResult result = service.importAttachments(emailId, lawyerId);

    assertThat(result.imported()).isEqualTo(1);
    verify(mailboxReader, never()).fetchAttachments(any(), any(), anyLong());
    verify(documentCommand, never()).upload(any(MultipartFile.class), any(), any(), any());
  }

  @Test
  void retriesPreviouslyRejectedAttachment() {
    EmailAttachment rejected = new EmailAttachment(emailId, 0, "contract.pdf", "application/pdf");
    rejected.markRejected("Файл отклонён");
    when(emailAttachmentRepository.findByEmailMessageIdOrderByPartIndexAsc(emailId))
        .thenReturn(List.of(rejected));
    when(mailboxReader.fetchAttachments(any(), any(), anyLong()))
        .thenReturn(List.of(loaded(0, "contract.pdf")));
    when(documentCommand.upload(any(MultipartFile.class), any(), eq(lawyerId), eq(caseId)))
        .thenReturn(
            new DocumentUploadResponse(
                UUID.randomUUID(), "contract", "contract.pdf", DocumentStatus.PROCESSING));

    EmailAttachmentImportResult result = service.importAttachments(emailId, lawyerId);

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.attachments()).hasSize(1);
  }

  @Test
  void rejectsImportForEmailWithoutCase() {
    message.clearLink();

    assertThatThrownBy(() -> service.importAttachments(emailId, lawyerId))
        .isInstanceOf(EmailNotLinkedToCaseException.class);
    verify(mailboxReader, never()).fetchAttachments(any(), any(), anyLong());
  }

  @Test
  void rejectsForeignEmail() {
    when(emailMessageRepository.findByIdAndUserId(emailId, lawyerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.importAttachments(emailId, lawyerId))
        .isInstanceOf(EmailMessageNotFoundException.class);
  }

  private FetchedAttachment loaded(int partIndex, String fileName) {
    return FetchedAttachment.loaded(
        partIndex, fileName, "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
  }
}
