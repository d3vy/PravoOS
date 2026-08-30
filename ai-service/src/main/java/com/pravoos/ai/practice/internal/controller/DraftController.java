package com.pravoos.ai.practice.internal.controller;

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
import com.pravoos.ai.shared.security.CallerContext;
import com.pravoos.ai.shared.util.SecureFileHeaders;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DraftController {

  private final DraftService draftService;
  private final DocxExportService docxExportService;

  public DraftController(DraftService draftService, DocxExportService docxExportService) {
    this.draftService = draftService;
    this.docxExportService = docxExportService;
  }

  @GetMapping("/api/ai/draft-types")
  public ResponseEntity<List<DraftTypeInfo>> listDraftTypes() {
    return ResponseEntity.ok(draftService.listDraftTypes());
  }

  @PostMapping("/api/ai/cases/{caseId}/drafts")
  public ResponseEntity<CaseDraftDto> generate(
      @PathVariable UUID caseId,
      @Valid @RequestBody GenerateDraftRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(draftService.generate(caseId, request, lawyerId, caller.orgIds()));
  }

  @GetMapping("/api/ai/cases/{caseId}/drafts")
  public ResponseEntity<List<CaseDraftSummaryDto>> listDrafts(
      @PathVariable UUID caseId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(draftService.findByCase(caseId, lawyerId, caller.orgIds()));
  }

  @GetMapping("/api/ai/drafts/{draftId}")
  public ResponseEntity<CaseDraftDto> getDraft(@PathVariable UUID draftId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(draftService.getDraft(draftId, lawyerId, caller.orgIds()));
  }

  @PutMapping("/api/ai/drafts/{draftId}")
  public ResponseEntity<CaseDraftDto> updateDraft(
      @PathVariable UUID draftId,
      @Valid @RequestBody UpdateDraftRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        draftService.updateContent(draftId, request, lawyerId, caller.orgIds()));
  }

  @GetMapping("/api/ai/drafts/{draftId}/versions")
  public ResponseEntity<List<CaseDraftVersionDto>> listVersions(
      @PathVariable UUID draftId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(draftService.listVersions(draftId, lawyerId, caller.orgIds()));
  }

  @PostMapping("/api/ai/drafts/{draftId}/versions/{versionId}/restore")
  public ResponseEntity<CaseDraftDto> restoreVersion(
      @PathVariable UUID draftId, @PathVariable UUID versionId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(
        draftService.restoreVersion(draftId, versionId, lawyerId, caller.orgIds()));
  }

  @PostMapping("/api/ai/drafts/{draftId}/refine")
  public ResponseEntity<RefineDraftResponse> refineDraft(
      @PathVariable UUID draftId,
      @Valid @RequestBody RefineDraftRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(draftService.refine(draftId, request, lawyerId, caller.orgIds()));
  }

  @GetMapping("/api/ai/drafts/{draftId}/download")
  public ResponseEntity<byte[]> downloadDraft(@PathVariable UUID draftId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    CaseDraft draft = draftService.requireVisibleDraft(draftId, lawyerId, caller.orgIds());
    byte[] docxBytes = docxExportService.export(draft.getTitle(), draft.getContent());

    String fileName = draft.getTitle().replaceAll("[^а-яА-Яa-zA-Z0-9]", "_") + ".docx";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
    SecureFileHeaders.apply(headers);

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .body(docxBytes);
  }
}
