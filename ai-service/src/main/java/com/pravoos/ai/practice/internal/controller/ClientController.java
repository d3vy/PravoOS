package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.ClientDetailResponse;
import com.pravoos.ai.practice.internal.dto.ClientResponse;
import com.pravoos.ai.practice.internal.dto.ConsentResponse;
import com.pravoos.ai.practice.internal.dto.CreateClientRequest;
import com.pravoos.ai.practice.internal.dto.PersonalDataExportResponse;
import com.pravoos.ai.practice.internal.dto.UpdateClientRequest;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.shared.dto.PortalInviteStatusResponse;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.service.AccessAuditService;
import com.pravoos.ai.shared.util.PagedResponse;
import com.pravoos.common.web.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/clients")
public class ClientController {

  private final ClientService clientService;
  private final AccessAuditService accessAuditService;

  public ClientController(ClientService clientService, AccessAuditService accessAuditService) {
    this.clientService = clientService;
    this.accessAuditService = accessAuditService;
  }

  @PostMapping
  public ResponseEntity<ClientResponse> create(
      @Valid @RequestBody CreateClientRequest request, Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request, lawyerId));
  }

  @GetMapping
  public ResponseEntity<List<ClientResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication) {
    return PagedResponse.of(
        clientService.findByLawyer(SecurityUtils.currentUserId(authentication), page, size));
  }

  @GetMapping("/{clientId}")
  public ResponseEntity<ClientDetailResponse> get(
      @PathVariable UUID clientId, Authentication authentication, HttpServletRequest request) {
    ClientDetailResponse client =
        clientService.get(clientId, SecurityUtils.currentUserId(authentication));
    accessAuditService.record(authentication, AuditAction.CLIENT_VIEW, clientId, request);
    return ResponseEntity.ok(client);
  }

  @PutMapping("/{clientId}")
  public ResponseEntity<ClientResponse> update(
      @PathVariable UUID clientId,
      @Valid @RequestBody UpdateClientRequest request,
      Authentication authentication) {
    UUID lawyerId = SecurityUtils.currentUserId(authentication);
    return ResponseEntity.ok(clientService.update(clientId, request, lawyerId));
  }

  @PostMapping("/{clientId}/portal/invite")
  public ResponseEntity<Void> invitePortal(
      @PathVariable UUID clientId, Authentication authentication) {
    clientService.invitePortal(clientId, SecurityUtils.currentUserId(authentication));
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/{clientId}/portal/invite")
  public ResponseEntity<PortalInviteStatusResponse> portalInviteStatus(
      @PathVariable UUID clientId, Authentication authentication) {
    return ResponseEntity.ok(
        clientService.portalInviteStatus(clientId, SecurityUtils.currentUserId(authentication)));
  }

  @DeleteMapping("/{clientId}/portal/invite")
  public ResponseEntity<Void> revokePortalInvite(
      @PathVariable UUID clientId, Authentication authentication) {
    clientService.revokePortalInvite(clientId, SecurityUtils.currentUserId(authentication));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{clientId}/consent")
  public ResponseEntity<ConsentResponse> consent(
      @PathVariable UUID clientId, Authentication authentication) {
    ConsentResponse consent =
        clientService.currentConsent(clientId, SecurityUtils.currentUserId(authentication));
    return consent == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(consent);
  }

  @PostMapping("/{clientId}/consent")
  public ResponseEntity<ConsentResponse> grantConsent(
      @PathVariable UUID clientId, Authentication authentication, HttpServletRequest request) {
    ConsentResponse consent =
        clientService.grantConsent(clientId, SecurityUtils.currentUserId(authentication));
    accessAuditService.record(authentication, AuditAction.CONSENT_GRANT, clientId, request);
    return ResponseEntity.ok(consent);
  }

  @DeleteMapping("/{clientId}/consent")
  public ResponseEntity<Void> revokeConsent(
      @PathVariable UUID clientId, Authentication authentication, HttpServletRequest request) {
    clientService.revokeConsent(clientId, SecurityUtils.currentUserId(authentication));
    accessAuditService.record(authentication, AuditAction.CONSENT_REVOKE, clientId, request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{clientId}/personal-data-export")
  public ResponseEntity<PersonalDataExportResponse> exportPersonalData(
      @PathVariable UUID clientId, Authentication authentication, HttpServletRequest request) {
    PersonalDataExportResponse export =
        clientService.exportPersonalData(clientId, SecurityUtils.currentUserId(authentication));
    accessAuditService.record(authentication, AuditAction.PERSONAL_DATA_EXPORT, clientId, request);
    return ResponseEntity.ok(export);
  }

  @DeleteMapping("/{clientId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "false") boolean cascade,
      Authentication authentication) {
    clientService.delete(clientId, SecurityUtils.currentUserId(authentication), cascade);
    return ResponseEntity.noContent().build();
  }
}
