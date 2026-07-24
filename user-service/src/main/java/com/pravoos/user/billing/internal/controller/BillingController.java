package com.pravoos.user.billing.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.billing.internal.dto.BillingStatusResponse;
import com.pravoos.user.billing.internal.dto.CheckoutRequest;
import com.pravoos.user.billing.internal.dto.CheckoutResponse;
import com.pravoos.user.billing.internal.dto.PaymentResponse;
import com.pravoos.user.billing.internal.dto.PlanResponse;
import com.pravoos.user.billing.internal.service.PaymentService;
import com.pravoos.user.billing.internal.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/billing")
public class BillingController {

  private final SubscriptionService subscriptionService;
  private final PaymentService paymentService;

  public BillingController(SubscriptionService subscriptionService, PaymentService paymentService) {
    this.subscriptionService = subscriptionService;
    this.paymentService = paymentService;
  }

  @GetMapping
  public ResponseEntity<BillingStatusResponse> getBilling(Authentication authentication) {
    return ResponseEntity.ok(
        subscriptionService.getStatus(SecurityUtils.currentUserId(authentication)));
  }

  @GetMapping("/plans")
  public ResponseEntity<List<PlanResponse>> getPlans() {
    return ResponseEntity.ok(subscriptionService.listPlans());
  }

  @PostMapping("/subscribe")
  public ResponseEntity<CheckoutResponse> subscribe(
      @Valid @RequestBody CheckoutRequest request, Authentication authentication) {
    return ResponseEntity.ok(
        paymentService.startCheckout(
            SecurityUtils.currentUserId(authentication), request.planCode()));
  }

  @PostMapping("/cancel")
  public ResponseEntity<BillingStatusResponse> cancel(Authentication authentication) {
    return ResponseEntity.ok(
        subscriptionService.cancel(SecurityUtils.currentUserId(authentication)));
  }

  @GetMapping("/payments")
  public ResponseEntity<List<PaymentResponse>> getPayments(Authentication authentication) {
    return ResponseEntity.ok(paymentService.history(SecurityUtils.currentUserId(authentication)));
  }
}
