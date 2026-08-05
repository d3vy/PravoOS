package com.pravoos.user.privacy.internal.controller;

import com.pravoos.user.privacy.internal.dto.SubjectRequestResponse;
import com.pravoos.user.privacy.internal.service.PersonalDataService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/privacy")
public class PrivacyAdminController {

  private final PersonalDataService personalDataService;

  public PrivacyAdminController(PersonalDataService personalDataService) {
    this.personalDataService = personalDataService;
  }

  @GetMapping("/requests")
  public ResponseEntity<List<SubjectRequestResponse>> pending() {
    return ResponseEntity.ok(personalDataService.pendingRequests());
  }

  @PostMapping("/requests/{requestId}/complete")
  public ResponseEntity<SubjectRequestResponse> complete(
      @PathVariable UUID requestId, @RequestBody(required = false) Map<String, String> body) {
    String note = body == null ? null : body.get("note");
    return ResponseEntity.ok(personalDataService.completeRequest(requestId, note));
  }
}
