package com.pravoos.user.collaboration.internal.controller;

import com.pravoos.user.collaboration.internal.dto.OrgMembershipCheckResponse;
import com.pravoos.user.collaboration.internal.service.OrganizationService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/org")
public class InternalOrganizationController {

  private final OrganizationService organizationService;

  public InternalOrganizationController(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @GetMapping("/{orgId}/members/{userId}")
  public ResponseEntity<OrgMembershipCheckResponse> checkMembership(
      @PathVariable UUID orgId, @PathVariable UUID userId) {
    return ResponseEntity.ok(
        new OrgMembershipCheckResponse(organizationService.isMember(orgId, userId)));
  }
}
