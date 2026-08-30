package com.pravoos.ai.core.internal.controller;

import com.pravoos.ai.core.internal.dto.ContractReviewDto;
import com.pravoos.ai.core.internal.dto.CreateContractReviewRequest;
import com.pravoos.ai.core.internal.service.ContractReviewService;
import com.pravoos.ai.shared.security.CallerContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
      @Valid @RequestBody CreateContractReviewRequest request, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(contractReviewService.review(request.documentId(), lawyerId, caller.orgIds()));
  }

  @GetMapping
  public ResponseEntity<List<ContractReviewDto>> listByCase(
      @RequestParam UUID caseId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(contractReviewService.findByCase(caseId, lawyerId, caller.orgIds()));
  }

  @GetMapping("/{reviewId}")
  public ResponseEntity<ContractReviewDto> get(@PathVariable UUID reviewId, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(contractReviewService.get(reviewId, lawyerId, caller.orgIds()));
  }
}
