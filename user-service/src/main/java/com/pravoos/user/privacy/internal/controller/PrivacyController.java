package com.pravoos.user.privacy.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.privacy.internal.config.PrivacyProperties;
import com.pravoos.user.privacy.internal.dto.ConsentResponse;
import com.pravoos.user.privacy.internal.dto.ErasureRequest;
import com.pravoos.user.privacy.internal.dto.PersonalDataExportResponse;
import com.pravoos.user.privacy.internal.dto.SubjectRequestResponse;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import com.pravoos.user.privacy.internal.service.ConsentService;
import com.pravoos.user.privacy.internal.service.PersonalDataService;
import com.pravoos.user.shared.exception.MandatoryConsentException;
import com.pravoos.user.shared.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/privacy")
public class PrivacyController {

  private final ConsentService consentService;
  private final PersonalDataService personalDataService;
  private final PrivacyProperties privacyProperties;

  public PrivacyController(
      ConsentService consentService,
      PersonalDataService personalDataService,
      PrivacyProperties privacyProperties) {
    this.consentService = consentService;
    this.personalDataService = personalDataService;
    this.privacyProperties = privacyProperties;
  }

  @GetMapping("/policy")
  public ResponseEntity<Map<String, String>> policy() {
    return ResponseEntity.ok(
        Map.of(
            "policyVersion", privacyProperties.resolvedPolicyVersion(),
            "operator", privacyProperties.resolvedOperatorName()));
  }

  @GetMapping("/consents")
  public ResponseEntity<List<ConsentResponse>> consents(Authentication authentication) {
    return ResponseEntity.ok(consentService.list(SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping("/consents/{purpose}")
  public ResponseEntity<ConsentResponse> grant(
      @PathVariable ConsentPurpose purpose,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        consentService.grant(
            SecurityUtils.currentUserId(authentication),
            purpose,
            ClientIpResolver.resolve(httpRequest),
            httpRequest.getHeader("User-Agent")));
  }

  @DeleteMapping("/consents/{purpose}")
  public ResponseEntity<Void> revoke(
      @PathVariable ConsentPurpose purpose, Authentication authentication) {
    if (purpose.isMandatory()) {
      throw new MandatoryConsentException();
    }
    consentService.revoke(SecurityUtils.currentUserId(authentication), purpose);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/export")
  public ResponseEntity<PersonalDataExportResponse> export(
      Authentication authentication, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        personalDataService.export(
            SecurityUtils.currentUserId(authentication), ClientIpResolver.resolve(httpRequest)));
  }

  @GetMapping("/requests")
  public ResponseEntity<List<SubjectRequestResponse>> requests(Authentication authentication) {
    return ResponseEntity.ok(
        personalDataService.myRequests(SecurityUtils.currentUserId(authentication)));
  }

  @PostMapping("/erase")
  public ResponseEntity<SubjectRequestResponse> erase(
      @Valid @RequestBody ErasureRequest request,
      Authentication authentication,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        personalDataService.requestErasure(
            SecurityUtils.currentUserId(authentication),
            request.password(),
            ClientIpResolver.resolve(httpRequest)));
  }
}
