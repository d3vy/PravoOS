package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.BillingProfileRequest;
import com.pravoos.ai.practice.internal.dto.BillingProfileResponse;
import com.pravoos.ai.practice.internal.service.BillingProfileService;
import com.pravoos.ai.shared.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/billing-profile")
public class BillingProfileController {

  private final BillingProfileService billingProfileService;

  public BillingProfileController(BillingProfileService billingProfileService) {
    this.billingProfileService = billingProfileService;
  }

  @GetMapping
  public ResponseEntity<BillingProfileResponse> get(CallerContext caller) {
    return billingProfileService
        .find(caller.userId())
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping
  public ResponseEntity<BillingProfileResponse> save(
      @Valid @RequestBody BillingProfileRequest request, CallerContext caller) {
    return ResponseEntity.ok(billingProfileService.save(request, caller.userId()));
  }
}
