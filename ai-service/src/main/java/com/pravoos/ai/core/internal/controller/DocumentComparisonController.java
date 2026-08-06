package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.ComparisonExportFile;
import com.pravoos.ai.core.internal.dto.CreateComparisonRequest;
import com.pravoos.ai.core.internal.dto.DocumentComparisonDto;
import com.pravoos.ai.core.internal.service.DocumentComparisonExportService;
import com.pravoos.ai.core.internal.service.DocumentComparisonService;
import com.pravoos.ai.shared.util.SecureFileHeaders;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/document-comparisons")
public class DocumentComparisonController {

  private final DocumentComparisonService documentComparisonService;
  private final DocumentComparisonExportService documentComparisonExportService;

  public DocumentComparisonController(
      DocumentComparisonService documentComparisonService,
      DocumentComparisonExportService documentComparisonExportService) {
    this.documentComparisonService = documentComparisonService;
    this.documentComparisonExportService = documentComparisonExportService;
  }

  @PostMapping
  public ResponseEntity<DocumentComparisonDto> create(
      @Valid @RequestBody CreateComparisonRequest request, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            documentComparisonService.compare(
                request.baseDocumentId(),
                request.revisedDocumentId(),
                lawyerId,
                SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping
  public ResponseEntity<List<DocumentComparisonDto>> listByCase(
      @RequestParam UUID caseId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        documentComparisonService.findByCase(
            caseId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{comparisonId}")
  public ResponseEntity<DocumentComparisonDto> get(
      @PathVariable UUID comparisonId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        documentComparisonService.get(
            comparisonId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{comparisonId}/export.docx")
  public ResponseEntity<byte[]> exportDocx(
      @PathVariable UUID comparisonId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    ComparisonExportFile file =
        documentComparisonExportService.exportDocx(
            comparisonId, lawyerId, SecurityUtils.currentOrgIds(authentication));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentDisposition(
        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build());
    SecureFileHeaders.apply(headers);

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType(file.contentType()))
        .body(file.content());
  }
}
