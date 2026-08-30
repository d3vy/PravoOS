package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.ClientDetailResponse;
import com.pravoos.ai.practice.internal.dto.ClientResponse;
import com.pravoos.ai.practice.internal.dto.ConflictHit;
import com.pravoos.ai.practice.internal.dto.ConsentResponse;
import com.pravoos.ai.practice.internal.dto.CreateClientRequest;
import com.pravoos.ai.practice.internal.dto.PersonalDataExportResponse;
import com.pravoos.ai.practice.internal.dto.UpdateClientRequest;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.practice.internal.service.ConflictCheckService;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.shared.dto.PortalInviteStatusResponse;
import com.pravoos.ai.shared.model.enums.AuditAction;
import com.pravoos.ai.shared.security.CallerContext;
import com.pravoos.ai.shared.service.AccessAuditService;
import com.pravoos.ai.shared.util.PagedResponse;
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
  private final ConflictCheckService conflictCheckService;
  private final AccessAuditService accessAuditService;

  public ClientController(
      ClientService clientService,
      ConflictCheckService conflictCheckService,
      AccessAuditService accessAuditService) {
    this.clientService = clientService;
    this.conflictCheckService = conflictCheckService;
    this.accessAuditService = accessAuditService;
  }

  @PostMapping
  public ResponseEntity<ClientResponse> create(
      @Valid @RequestBody CreateClientRequest request, CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.status(HttpStatus.CREATED).body(clientService.create(request, lawyerId));
  }

  @GetMapping("/conflict-check")
  public ResponseEntity<List<ConflictHit>> conflictCheck(
      @RequestParam String name,
      @RequestParam(required = false) UUID excludeClientId,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(conflictCheckService.check(lawyerId, name, excludeClientId));
  }

  @GetMapping
  public ResponseEntity<List<ClientResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      CallerContext caller) {
    return PagedResponse.of(clientService.findByLawyer(caller.userId(), page, size));
  }

  @GetMapping("/{clientId}")
  public ResponseEntity<ClientDetailResponse> get(
      @PathVariable UUID clientId,
      Authentication authentication,
      CallerContext caller,
      HttpServletRequest request) {
    ClientDetailResponse client = clientService.get(clientId, caller.userId());
    accessAuditService.record(authentication, AuditAction.CLIENT_VIEW, clientId, request);
    return ResponseEntity.ok(client);
  }

  @PutMapping("/{clientId}")
  public ResponseEntity<ClientResponse> update(
      @PathVariable UUID clientId,
      @Valid @RequestBody UpdateClientRequest request,
      CallerContext caller) {
    UUID lawyerId = caller.userId();
    return ResponseEntity.ok(clientService.update(clientId, request, lawyerId));
  }

  @PostMapping("/{clientId}/portal/invite")
  public ResponseEntity<Void> invitePortal(@PathVariable UUID clientId, CallerContext caller) {
    clientService.invitePortal(clientId, caller.userId());
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/{clientId}/portal/invite")
  public ResponseEntity<PortalInviteStatusResponse> portalInviteStatus(
      @PathVariable UUID clientId, CallerContext caller) {
    return ResponseEntity.ok(clientService.portalInviteStatus(clientId, caller.userId()));
  }

  @DeleteMapping("/{clientId}/portal/invite")
  public ResponseEntity<Void> revokePortalInvite(
      @PathVariable UUID clientId, CallerContext caller) {
    clientService.revokePortalInvite(clientId, caller.userId());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{clientId}/consent")
  public ResponseEntity<ConsentResponse> consent(
      @PathVariable UUID clientId, CallerContext caller) {
    ConsentResponse consent = clientService.currentConsent(clientId, caller.userId());
    return consent == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(consent);
  }

  @PostMapping("/{clientId}/consent")
  public ResponseEntity<ConsentResponse> grantConsent(
      @PathVariable UUID clientId,
      Authentication authentication,
      CallerContext caller,
      HttpServletRequest request) {
    ConsentResponse consent = clientService.grantConsent(clientId, caller.userId());
    accessAuditService.record(authentication, AuditAction.CONSENT_GRANT, clientId, request);
    return ResponseEntity.ok(consent);
  }

  @DeleteMapping("/{clientId}/consent")
  public ResponseEntity<Void> revokeConsent(
      @PathVariable UUID clientId,
      Authentication authentication,
      CallerContext caller,
      HttpServletRequest request) {
    clientService.revokeConsent(clientId, caller.userId());
    accessAuditService.record(authentication, AuditAction.CONSENT_REVOKE, clientId, request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{clientId}/personal-data-export")
  public ResponseEntity<PersonalDataExportResponse> exportPersonalData(
      @PathVariable UUID clientId,
      Authentication authentication,
      CallerContext caller,
      HttpServletRequest request) {
    PersonalDataExportResponse export = clientService.exportPersonalData(clientId, caller.userId());
    accessAuditService.record(authentication, AuditAction.PERSONAL_DATA_EXPORT, clientId, request);
    return ResponseEntity.ok(export);
  }

  @DeleteMapping("/{clientId}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID clientId,
      @RequestParam(defaultValue = "false") boolean cascade,
      Authentication authentication) {
    clientService.delete(clientId, DeletionActor.of(authentication), cascade);
    return ResponseEntity.noContent().build();
  }
}
