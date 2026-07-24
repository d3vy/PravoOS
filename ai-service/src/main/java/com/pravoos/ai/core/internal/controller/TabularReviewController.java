package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.CreateTabularReviewRequest;
import com.pravoos.ai.core.internal.dto.ReviewExportFile;
import com.pravoos.ai.core.internal.dto.TabularReviewDto;
import com.pravoos.ai.core.internal.dto.TabularReviewSummaryDto;
import com.pravoos.ai.core.internal.service.TabularReviewExportService;
import com.pravoos.ai.core.internal.service.TabularReviewService;
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
@RequestMapping("/api/ai/tabular-reviews")
public class TabularReviewController {

  private final TabularReviewService tabularReviewService;
  private final TabularReviewExportService tabularReviewExportService;

  public TabularReviewController(
      TabularReviewService tabularReviewService,
      TabularReviewExportService tabularReviewExportService) {
    this.tabularReviewService = tabularReviewService;
    this.tabularReviewExportService = tabularReviewExportService;
  }

  @PostMapping
  public ResponseEntity<TabularReviewDto> create(
      @Valid @RequestBody CreateTabularReviewRequest request, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            tabularReviewService.create(
                request, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping
  public ResponseEntity<List<TabularReviewSummaryDto>> listByCase(
      @RequestParam UUID caseId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        tabularReviewService.findByCase(
            caseId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{reviewId}")
  public ResponseEntity<TabularReviewDto> get(
      @PathVariable UUID reviewId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        tabularReviewService.get(reviewId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> delete(@PathVariable UUID reviewId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    tabularReviewService.delete(reviewId, lawyerId, SecurityUtils.currentOrgIds(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{reviewId}/export")
  public ResponseEntity<byte[]> export(
      @PathVariable UUID reviewId,
      @RequestParam(defaultValue = "xlsx") String format,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    ReviewExportFile file =
        tabularReviewExportService.export(
            reviewId, format, lawyerId, SecurityUtils.currentOrgIds(authentication));

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
