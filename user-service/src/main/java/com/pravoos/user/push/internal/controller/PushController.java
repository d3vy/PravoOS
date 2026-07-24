package com.pravoos.user.push.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.push.internal.config.VapidProperties;
import com.pravoos.user.push.internal.dto.PushConfigResponse;
import com.pravoos.user.push.internal.dto.RegisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.dto.UnregisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.service.PushSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/push")
public class PushController {

  private final PushSubscriptionService pushSubscriptionService;
  private final VapidProperties vapidProperties;

  public PushController(
      PushSubscriptionService pushSubscriptionService, VapidProperties vapidProperties) {
    this.pushSubscriptionService = pushSubscriptionService;
    this.vapidProperties = vapidProperties;
  }

  @GetMapping("/config")
  public ResponseEntity<PushConfigResponse> config() {
    if (!vapidProperties.configured()) {
      return ResponseEntity.ok(PushConfigResponse.disabled());
    }
    return ResponseEntity.ok(new PushConfigResponse(true, vapidProperties.publicKey()));
  }

  @PostMapping("/subscriptions")
  public ResponseEntity<Void> subscribe(
      @Valid @RequestBody RegisterPushSubscriptionRequest request, Authentication authentication) {
    pushSubscriptionService.register(SecurityUtils.currentUserId(authentication), request);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/subscriptions/remove")
  public ResponseEntity<Void> unsubscribe(
      @Valid @RequestBody UnregisterPushSubscriptionRequest request,
      Authentication authentication) {
    pushSubscriptionService.unregister(
        SecurityUtils.currentUserId(authentication), request.endpoint());
    return ResponseEntity.noContent().build();
  }
}
