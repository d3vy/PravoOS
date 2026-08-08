package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

  @Mock private DocumentCommand documentCommand;
  @Mock private DocumentAccess documentAccess;

  private ChatAttachmentService service;

  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new ChatAttachmentService(documentCommand, documentAccess);
  }

  @Test
  void uploadDelegatesToDocumentCommand() {
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "договор.pdf", "application/pdf", "content".getBytes());
    when(documentCommand.uploadChatAttachment(file, "Договор", lawyerId))
        .thenReturn(
            new DocumentUploadResponse(
                documentId, "Договор", "договор.pdf", DocumentStatus.PROCESSING));

    DocumentUploadResponse response = service.upload(file, "Договор", lawyerId);

    assertThat(response.id()).isEqualTo(documentId);
    verify(documentCommand).uploadChatAttachment(file, "Договор", lawyerId);
  }

  @Test
  void findAllDelegatesToDocumentAccess() {
    when(documentAccess.findChatAttachments(lawyerId))
        .thenReturn(
            List.of(
                new DocumentResponse(
                    UUID.randomUUID(),
                    "Договор",
                    "договор.pdf",
                    "application/pdf",
                    DocumentStatus.READY,
                    LocalDateTime.now(ZoneOffset.UTC),
                    false)));

    List<DocumentResponse> result = service.findAll(lawyerId);

    assertThat(result).hasSize(1);
    verify(documentAccess).findChatAttachments(eq(lawyerId));
  }

  @Test
  void findAllReturnsEmptyListWhenNoAttachments() {
    when(documentAccess.findChatAttachments(lawyerId)).thenReturn(List.of());

    assertThat(service.findAll(lawyerId)).isEmpty();
  }
}
