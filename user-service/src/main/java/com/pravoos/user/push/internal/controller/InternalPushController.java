package com.pravoos.user.push.internal.controller;

import com.pravoos.user.push.internal.dto.PushSubscriptionView;
import com.pravoos.user.push.internal.dto.UnregisterPushSubscriptionRequest;
import com.pravoos.user.push.internal.service.PushSubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/push")
public class InternalPushController {

  private final PushSubscriptionService pushSubscriptionService;

  public InternalPushController(PushSubscriptionService pushSubscriptionService) {
    this.pushSubscriptionService = pushSubscriptionService;
  }

  @GetMapping("/subscriptions/{userId}")
  public ResponseEntity<List<PushSubscriptionView>> subscriptions(@PathVariable UUID userId) {
    return ResponseEntity.ok(pushSubscriptionService.subscriptionsOf(userId));
  }

  @PostMapping("/subscriptions/prune")
  public ResponseEntity<Void> prune(@Valid @RequestBody UnregisterPushSubscriptionRequest request) {
    pushSubscriptionService.prune(request.endpoint());
    return ResponseEntity.noContent().build();
  }
}
