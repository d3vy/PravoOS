package com.pravoos.user.billing.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.billing.internal.dto.BillingStatusResponse;
import com.pravoos.user.billing.internal.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/billing")
public class BillingController {

    private final SubscriptionService subscriptionService;

    public BillingController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public ResponseEntity<BillingStatusResponse> getBilling(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getStatus(SecurityUtils.currentUserId(authentication)));
    }
}
