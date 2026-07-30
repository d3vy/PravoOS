package com.pravoos.user.identity.internal.controller;

import com.pravoos.user.identity.internal.dto.DigestPreferenceRequest;
import com.pravoos.user.identity.internal.dto.DigestPreferenceResponse;
import com.pravoos.user.identity.internal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

  private final UserService userService;

  public InternalUserController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping("/digest-preferences")
  public ResponseEntity<DigestPreferenceResponse> digestPreferences(
      @Valid @RequestBody DigestPreferenceRequest request) {
    return ResponseEntity.ok(
        new DigestPreferenceResponse(userService.filterDigestEnabled(request.userIds())));
  }
}
