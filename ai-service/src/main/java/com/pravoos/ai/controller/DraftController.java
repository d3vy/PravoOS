package com.pravoos.ai.controller;

import com.pravoos.ai.model.dto.CaseDraftDto;
import com.pravoos.ai.model.dto.CaseDraftSummaryDto;
import com.pravoos.ai.model.dto.DraftTypeInfo;
import com.pravoos.ai.model.dto.GenerateDraftRequest;
import com.pravoos.ai.model.entity.CaseDraft;
import com.pravoos.ai.security.SecurityUtils;
import com.pravoos.ai.service.DocxExportService;
import com.pravoos.ai.service.DraftService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(draftService.generate(caseId, request, lawyerId));
    }

    @GetMapping("/api/ai/cases/{caseId}/drafts")
    public ResponseEntity<List<CaseDraftSummaryDto>> listDrafts(
            @PathVariable UUID caseId,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(draftService.findByCase(caseId, lawyerId));
    }

    @GetMapping("/api/ai/drafts/{draftId}/download")
    public ResponseEntity<byte[]> downloadDraft(
            @PathVariable UUID draftId,
            Authentication authentication) {
        UUID lawyerId = SecurityUtils.currentUserId(authentication);
        CaseDraft draft = draftService.requireOwnedDraft(draftId, lawyerId);
        byte[] docxBytes = docxExportService.export(draft.getTitle(), draft.getContent());

        String fileName = draft.getTitle().replaceAll("[^а-яА-Яa-zA-Z0-9]", "_") + ".docx";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docxBytes);
    }
}
