package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.ContractReviewDto;
import com.pravoos.ai.core.internal.dto.CreateContractReviewRequest;
import com.pravoos.ai.core.internal.service.ContractReviewService;
import com.pravoos.common.web.SecurityUtils;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/contract-reviews")
public class ContractReviewController {

  private final ContractReviewService contractReviewService;

  public ContractReviewController(ContractReviewService contractReviewService) {
    this.contractReviewService = contractReviewService;
  }

  @PostMapping
  public ResponseEntity<ContractReviewDto> create(
      @Valid @RequestBody CreateContractReviewRequest request, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            contractReviewService.review(
                request.documentId(), lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping
  public ResponseEntity<List<ContractReviewDto>> listByCase(
      @RequestParam UUID caseId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        contractReviewService.findByCase(
            caseId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }

  @GetMapping("/{reviewId}")
  public ResponseEntity<ContractReviewDto> get(
      @PathVariable UUID reviewId, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(
        contractReviewService.get(reviewId, lawyerId, SecurityUtils.currentOrgIds(authentication)));
  }
}
