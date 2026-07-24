package com.pravoos.user.identity.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.identity.internal.dto.MfaCodeRequest;
import com.pravoos.user.identity.internal.dto.MfaSetupResponse;
import com.pravoos.user.identity.internal.dto.MfaStatusResponse;
import com.pravoos.user.identity.internal.service.MfaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/mfa")
public class MfaController {

  private final MfaService mfaService;

  public MfaController(MfaService mfaService) {
    this.mfaService = mfaService;
  }

  @GetMapping
  public ResponseEntity<MfaStatusResponse> status(Authentication authentication) {
    return ResponseEntity.ok(mfaService.status(SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping("/setup")
  public ResponseEntity<MfaSetupResponse> setup(Authentication authentication) {
    return ResponseEntity.ok(mfaService.setup(SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping("/enable")
  public ResponseEntity<Void> enable(
      @Valid @RequestBody MfaCodeRequest request, Authentication authentication) {
    mfaService.enable(SecurityUtils.currentUserId(authentication), request.code());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/disable")
  public ResponseEntity<Void> disable(
      @Valid @RequestBody MfaCodeRequest request, Authentication authentication) {
    mfaService.disable(SecurityUtils.currentUserId(authentication), request.code());
    return ResponseEntity.noContent().build();
  }
}
