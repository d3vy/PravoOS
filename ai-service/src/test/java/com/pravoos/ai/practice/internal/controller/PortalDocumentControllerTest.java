package com.pravoos.ai.practice.internal.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.practice.internal.service.PortalDocumentService;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import com.pravoos.ai.shared.service.AccessAuditService;
import com.pravoos.common.web.OrgContext;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PortalDocumentControllerTest {

  @Mock private PortalDocumentService portalDocumentService;
  @Mock private AccessAuditService accessAuditService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final UUID clientId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    PortalDocumentController controller =
        new PortalDocumentController(portalDocumentService, accessAuditService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void listReturnsDocumentsVisibleToClient() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID documentId = UUID.randomUUID();
    when(portalDocumentService.listCaseDocuments(caseId, List.of(clientId)))
        .thenReturn(
            List.of(
                new DocumentResponse(
                    documentId,
                    "Contract",
                    "contract.pdf",
                    "pdf",
                    DocumentStatus.READY,
                    LocalDateTime.now(ZoneOffset.UTC),
                    true)));

    mockMvc
        .perform(get("/api/ai/portal/cases/{caseId}/documents", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(documentId.toString()));
  }

  @Test
  void listReturns404WhenCaseNotVisible() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    when(portalDocumentService.listCaseDocuments(caseId, List.of(clientId)))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(get("/api/ai/portal/cases/{caseId}/documents", caseId).principal(authentication))
        .andExpect(status().isNotFound());
  }

  @Test
  void uploadReturns201WithCreatedDocument() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID documentId = UUID.randomUUID();
    when(portalDocumentService.uploadCaseDocument(
            eq(caseId), any(), eq("Contract"), eq(userId), eq(List.of(clientId))))
        .thenReturn(
            new DocumentUploadResponse(
                documentId, "Contract", "contract.pdf", DocumentStatus.PROCESSING));

    org.springframework.mock.web.MockMultipartFile file =
        new org.springframework.mock.web.MockMultipartFile(
            "file", "contract.pdf", "application/pdf", "content".getBytes());

    mockMvc
        .perform(
            multipart("/api/ai/portal/cases/{caseId}/documents", caseId)
                .file(file)
                .param("title", "Contract")
                .principal(authentication))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(documentId.toString()));
  }

  @Test
  void contentReturnsDocumentBytesAndRecordsAudit() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID documentId = UUID.randomUUID();
    DocumentContent content =
        new DocumentContent(
            new ByteArrayResource("file-bytes".getBytes()), "contract.pdf", "pdf", 10);
    when(portalDocumentService.downloadCaseDocument(caseId, documentId, List.of(clientId)))
        .thenReturn(content);

    mockMvc
        .perform(
            get("/api/ai/portal/cases/{caseId}/documents/{documentId}/content", caseId, documentId)
                .principal(authentication))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"));

    verify(accessAuditService)
        .record(eq(authentication), eq(AuditAction.DOCUMENT_DOWNLOAD), eq(documentId), any());
  }

  @Test
  void contentReturns404WhenDocumentNotFound() throws Exception {
    when(authentication.getDetails())
        .thenReturn(new OrgContext(List.of(), List.of(clientId), null));
    UUID documentId = UUID.randomUUID();
    when(portalDocumentService.downloadCaseDocument(caseId, documentId, List.of(clientId)))
        .thenThrow(new CaseNotFoundException(caseId));

    mockMvc
        .perform(
            get("/api/ai/portal/cases/{caseId}/documents/{documentId}/content", caseId, documentId)
                .principal(authentication))
        .andExpect(status().isNotFound());
  }
}
