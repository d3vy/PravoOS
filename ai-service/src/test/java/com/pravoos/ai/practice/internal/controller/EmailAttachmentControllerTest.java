package com.pravoos.ai.practice.internal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.practice.internal.dto.EmailAttachmentImportResult;
import com.pravoos.ai.practice.internal.dto.EmailAttachmentResponse;
import com.pravoos.ai.practice.internal.service.EmailAttachmentImportService;
import com.pravoos.ai.shared.exception.EmailMessageNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.EmailAttachmentStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class EmailAttachmentControllerTest {

  @Mock private EmailAttachmentImportService emailAttachmentImportService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID emailId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    EmailAttachmentController controller =
        new EmailAttachmentController(emailAttachmentImportService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
  }

  @Test
  void attachmentsReturnsListForEmail() throws Exception {
    UUID attachmentId = UUID.randomUUID();
    when(emailAttachmentImportService.findByEmail(emailId, lawyerId))
        .thenReturn(
            List.of(
                new EmailAttachmentResponse(
                    attachmentId,
                    0,
                    "договор.pdf",
                    "application/pdf",
                    1024,
                    EmailAttachmentStatus.IMPORTED,
                    null,
                    UUID.randomUUID(),
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/emails/{emailId}/attachments", emailId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(attachmentId.toString()));
  }

  @Test
  void attachmentsReturns404WhenEmailNotFound() throws Exception {
    when(emailAttachmentImportService.findByEmail(emailId, lawyerId))
        .thenThrow(new EmailMessageNotFoundException(emailId));

    mockMvc
        .perform(get("/api/ai/emails/{emailId}/attachments", emailId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void importAttachmentsReturnsSummary() throws Exception {
    when(emailAttachmentImportService.importAttachments(emailId, lawyerId))
        .thenReturn(EmailAttachmentImportResult.of(emailId, UUID.randomUUID(), List.of()));

    mockMvc
        .perform(
            post("/api/ai/emails/{emailId}/attachments/import", emailId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.emailId").value(emailId.toString()))
        .andExpect(jsonPath("$.imported").value(0));
  }
}
