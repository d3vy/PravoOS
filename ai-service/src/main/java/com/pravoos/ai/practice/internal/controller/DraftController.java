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
import com.pravoos.ai.shared.util.SecureFileHeaders;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.generate(caseId, request, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @GetMapping("/api/ai/cases/{caseId}/drafts")
    public ResponseEntity<List<CaseDraftSummaryDto>> listDrafts(
            @PathVariable UUID caseId,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.findByCase(caseId, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @GetMapping("/api/ai/drafts/{draftId}")
    public ResponseEntity<CaseDraftDto> getDraft(
            @PathVariable UUID draftId,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.getDraft(draftId, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @PutMapping("/api/ai/drafts/{draftId}")
    public ResponseEntity<CaseDraftDto> updateDraft(
            @PathVariable UUID draftId,
            @Valid @RequestBody UpdateDraftRequest request,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.updateContent(draftId, request, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @GetMapping("/api/ai/drafts/{draftId}/versions")
    public ResponseEntity<List<CaseDraftVersionDto>> listVersions(
            @PathVariable UUID draftId,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.listVersions(draftId, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @PostMapping("/api/ai/drafts/{draftId}/versions/{versionId}/restore")
    public ResponseEntity<CaseDraftDto> restoreVersion(
            @PathVariable UUID draftId,
            @PathVariable UUID versionId,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.restoreVersion(draftId, versionId, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @PostMapping("/api/ai/drafts/{draftId}/refine")
    public ResponseEntity<RefineDraftResponse> refineDraft(
            @PathVariable UUID draftId,
            @Valid @RequestBody RefineDraftRequest request,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.refine(draftId, request, lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
    }

    @GetMapping("/api/ai/drafts/{draftId}/download")
    public ResponseEntity<byte[]> downloadDraft(
            @PathVariable UUID draftId,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        CaseDraft draft = draftService.requireVisibleDraft(draftId, lawyerId,
                SecurityUtils.currentOrgIds(authentication));
        byte[] docxBytes = docxExportService.export(draft.getTitle(), draft.getContent());

        String fileName = draft.getTitle().replaceAll("[^а-яА-Яa-zA-Z0-9]", "_") + ".docx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
        SecureFileHeaders.apply(headers);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docxBytes);
    }
}
