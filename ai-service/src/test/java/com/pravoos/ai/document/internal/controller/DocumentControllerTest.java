package com.pravoos.ai.document.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pravoos.ai.document.api.DocumentContent;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import com.pravoos.ai.document.internal.dto.LegislationResponse;
import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
import com.pravoos.ai.shared.service.AccessAuditService;
import com.pravoos.ai.shared.util.PagedResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

  @Mock private DocumentService documentService;
  @Mock private AccessAuditService accessAuditService;
  @Mock private Authentication authentication;
  @Mock private RecycleBin recycleBin;

  private MockMvc mockMvc;
  private final UUID lawyerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    DocumentController controller =
        new DocumentController(documentService, accessAuditService, recycleBin);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .build();
  }

  private DocumentResponse documentResponse(UUID id) {
    return new DocumentResponse(
        id,
        "title",
        "file.pdf",
        "pdf",
        DocumentStatus.READY,
        LocalDateTime.now(ZoneOffset.UTC),
        false);
  }

  @Test
  void upload_returns201WithBody() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "contract.pdf", "application/pdf", "content".getBytes());
    when(documentService.uploadKnowledgeBaseDocument(any(), eq("My Title"), eq(lawyerId)))
        .thenReturn(
            new DocumentUploadResponse(
                documentId, "My Title", "contract.pdf", DocumentStatus.PROCESSING));

    mockMvc
        .perform(
            multipart("/api/ai/documents")
                .file(file)
                .param("title", "My Title")
                .principal(authentication))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(documentId.toString()))
        .andExpect(jsonPath("$.title").value("My Title"));
  }

  @Test
  void findAll_returnsPagedResponseWithTotalCountHeader() throws Exception {
    UUID documentId = UUID.randomUUID();
    when(documentService.findAll(0, 20))
        .thenReturn(new PageImpl<>(List.of(documentResponse(documentId))));

    mockMvc
        .perform(get("/api/ai/documents"))
        .andExpect(status().isOk())
        .andExpect(header().string(PagedResponse.TOTAL_COUNT_HEADER, "1"))
        .andExpect(jsonPath("$[0].id").value(documentId.toString()));
  }

  @Test
  void uploadLegislation_returns201WithBody() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "law.pdf", "application/pdf", "content".getBytes());
    when(documentService.uploadLegislation(
            any(), eq("ГК РФ"), eq("15"), eq(LocalDate.of(2024, 1, 1)), eq("title"), eq(lawyerId)))
        .thenReturn(
            new DocumentUploadResponse(documentId, "title", "law.pdf", DocumentStatus.PROCESSING));

    mockMvc
        .perform(
            multipart("/api/ai/documents/legislation")
                .file(file)
                .param("actCanonical", "ГК РФ")
                .param("articleNumber", "15")
                .param("editionDate", "2024-01-01")
                .param("title", "title")
                .principal(authentication))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(documentId.toString()));
  }

  @Test
  void findLegislation_returnsPagedResponse() throws Exception {
    UUID documentId = UUID.randomUUID();
    LegislationResponse response =
        new LegislationResponse(
            documentId,
            "title",
            "ГК РФ",
            "15",
            LocalDate.of(2024, 1, 1),
            DocumentStatus.READY,
            false);
    when(documentService.findLegislation(0, 20)).thenReturn(new PageImpl<>(List.of(response)));

    mockMvc
        .perform(get("/api/ai/documents/legislation"))
        .andExpect(status().isOk())
        .andExpect(header().string(PagedResponse.TOTAL_COUNT_HEADER, "1"))
        .andExpect(jsonPath("$[0].articleNumber").value("15"));
  }

  @Test
  void content_returnsInlineDisposition_forPdf() throws Exception {
    UUID documentId = UUID.randomUUID();
    byte[] bytes = "pdf-content".getBytes(StandardCharsets.UTF_8);
    when(documentService.loadContent(documentId))
        .thenReturn(
            new DocumentContent(new ByteArrayResource(bytes), "doc.pdf", "pdf", bytes.length));

    mockMvc
        .perform(get("/api/ai/documents/{id}/content", documentId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
        .andExpect(header().string("Content-Type", "application/pdf"));

    verify(accessAuditService)
        .record(eq(authentication), eq(AuditAction.DOCUMENT_DOWNLOAD), eq(documentId), any());
  }

  @Test
  void content_returnsAttachmentDisposition_forDocx() throws Exception {
    UUID documentId = UUID.randomUUID();
    byte[] bytes = "docx-content".getBytes(StandardCharsets.UTF_8);
    when(documentService.loadContent(documentId))
        .thenReturn(
            new DocumentContent(new ByteArrayResource(bytes), "doc.docx", "docx", bytes.length));

    mockMvc
        .perform(get("/api/ai/documents/{id}/content", documentId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
  }

  @Test
  void delete_movesDocumentToRecycleBin() throws Exception {
    UUID documentId = UUID.randomUUID();
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());

    mockMvc
        .perform(delete("/api/ai/documents/{id}", documentId).principal(authentication))
        .andExpect(status().isNoContent());

    verify(documentService).requireDeletable(documentId);
    verify(recycleBin)
        .moveToBin(eq(RecycleBinEntityType.DOCUMENT), eq(documentId.toString()), any());
  }
}
