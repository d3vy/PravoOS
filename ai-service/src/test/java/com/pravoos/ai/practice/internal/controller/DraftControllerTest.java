package com.pravoos.ai.practice.internal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.CaseDraftSummaryDto;
import com.pravoos.ai.practice.internal.dto.CaseDraftVersionDto;
import com.pravoos.ai.practice.internal.dto.DraftTypeInfo;
import com.pravoos.ai.practice.internal.dto.GenerateDraftRequest;
import com.pravoos.ai.practice.internal.dto.RefineDraftRequest;
import com.pravoos.ai.practice.internal.dto.RefineDraftResponse;
import com.pravoos.ai.practice.internal.dto.UpdateDraftRequest;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.service.DocxExportService;
import com.pravoos.ai.practice.internal.service.DraftService;
import com.pravoos.ai.shared.exception.GlobalExceptionHandler;
import com.pravoos.ai.shared.model.enums.DraftType;
import com.pravoos.ai.shared.security.CallerContextArgumentResolver;
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
class DraftControllerTest {

  @Mock private DraftService draftService;
  @Mock private DocxExportService docxExportService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final UUID lawyerId = UUID.randomUUID();
  private final UUID caseId = UUID.randomUUID();
  private final UUID draftId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    DraftController controller = new DraftController(draftService, docxExportService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new CallerContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private CaseDraftDto sampleDraft() {
    return new CaseDraftDto(
        draftId,
        caseId,
        DraftType.COMPLAINT.name(),
        DraftType.COMPLAINT.displayName(),
        "Исковое заявление",
        "Содержимое документа",
        LocalDateTime.now(ZoneOffset.UTC),
        LocalDateTime.now(ZoneOffset.UTC));
  }

  @Test
  void listDraftTypesReturnsAllTypes() throws Exception {
    when(draftService.listDraftTypes())
        .thenReturn(List.of(DraftTypeInfo.from(DraftType.COMPLAINT)));

    mockMvc
        .perform(get("/api/ai/draft-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(DraftType.COMPLAINT.name()));
  }

  @Test
  void generateReturnsDraft() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(draftService.generate(
            eq(caseId), any(GenerateDraftRequest.class), eq(lawyerId), anyList()))
        .thenReturn(sampleDraft());

    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/drafts", caseId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new GenerateDraftRequest(DraftType.COMPLAINT.name(), null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Исковое заявление"));
  }

  @Test
  void generateRejectsBlankDraftType() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/cases/{caseId}/drafts", caseId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new GenerateDraftRequest("", null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(draftService);
  }

  @Test
  void listDraftsReturnsSummaries() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(draftService.findByCase(eq(caseId), eq(lawyerId), anyList()))
        .thenReturn(
            List.of(
                new CaseDraftSummaryDto(
                    draftId,
                    caseId,
                    DraftType.COMPLAINT.name(),
                    DraftType.COMPLAINT.displayName(),
                    "Исковое заявление",
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/cases/{caseId}/drafts", caseId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(draftId.toString()));
  }

  @Test
  void getDraftReturnsDraft() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(draftService.getDraft(eq(draftId), eq(lawyerId), anyList())).thenReturn(sampleDraft());

    mockMvc
        .perform(get("/api/ai/drafts/{draftId}", draftId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(draftId.toString()));
  }

  @Test
  void updateDraftReturnsUpdatedDraft() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(draftService.updateContent(
            eq(draftId), any(UpdateDraftRequest.class), eq(lawyerId), anyList()))
        .thenReturn(sampleDraft());

    mockMvc
        .perform(
            put("/api/ai/drafts/{draftId}", draftId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(
                        new UpdateDraftRequest("Новое содержимое", "Правка"))))
        .andExpect(status().isOk());
  }

  @Test
  void updateDraftRejectsBlankContent() throws Exception {
    mockMvc
        .perform(
            put("/api/ai/drafts/{draftId}", draftId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new UpdateDraftRequest("", null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(draftService);
  }

  @Test
  void listVersionsReturnsVersions() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(draftService.listVersions(eq(draftId), eq(lawyerId), anyList()))
        .thenReturn(
            List.of(
                new CaseDraftVersionDto(
                    UUID.randomUUID(),
                    1,
                    "Первая версия",
                    "Текст",
                    LocalDateTime.now(ZoneOffset.UTC))));

    mockMvc
        .perform(get("/api/ai/drafts/{draftId}/versions", draftId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].versionNo").value(1));
  }

  @Test
  void restoreVersionReturnsRestoredDraft() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    UUID versionId = UUID.randomUUID();
    when(draftService.restoreVersion(eq(draftId), eq(versionId), eq(lawyerId), anyList()))
        .thenReturn(sampleDraft());

    mockMvc
        .perform(
            post("/api/ai/drafts/{draftId}/versions/{versionId}/restore", draftId, versionId)
                .principal(authentication))
        .andExpect(status().isOk());
  }

  @Test
  void refineDraftReturnsRevisedText() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    when(draftService.refine(eq(draftId), any(RefineDraftRequest.class), eq(lawyerId), anyList()))
        .thenReturn(new RefineDraftResponse("Улучшенный текст"));

    mockMvc
        .perform(
            post("/api/ai/drafts/{draftId}/refine", draftId)
                .principal(authentication)
                .contentType("application/json")
                .content(
                    objectMapper.writeValueAsString(new RefineDraftRequest("Сделай короче", null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revisedText").value("Улучшенный текст"));
  }

  @Test
  void refineDraftRejectsBlankInstruction() throws Exception {
    mockMvc
        .perform(
            post("/api/ai/drafts/{draftId}/refine", draftId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new RefineDraftRequest("", null))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(draftService);
  }

  @Test
  void downloadDraftReturnsDocxAttachment() throws Exception {
    when(authentication.getPrincipal()).thenReturn(lawyerId.toString());
    CaseDraft draft = new CaseDraft();
    draft.setTitle("Исковое заявление");
    draft.setContent("Содержимое документа");
    when(draftService.requireVisibleDraft(eq(draftId), eq(lawyerId), anyList())).thenReturn(draft);
    when(docxExportService.export(anyString(), anyString())).thenReturn(new byte[] {1, 2, 3});

    mockMvc
        .perform(get("/api/ai/drafts/{draftId}/download", draftId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Content-Disposition", org.hamcrest.Matchers.containsString(".docx")));
  }
}
