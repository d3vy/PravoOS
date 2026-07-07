package com.pravoos.user.collaboration.internal.controller;

import com.pravoos.user.collaboration.internal.dto.CreatePortalInviteRequest;
import com.pravoos.user.collaboration.internal.dto.PortalInviteStatusResponse;
import com.pravoos.user.collaboration.internal.service.ClientPortalInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/portal-invites")
public class InternalPortalInviteController {

    private final ClientPortalInviteService clientPortalInviteService;

    public InternalPortalInviteController(ClientPortalInviteService clientPortalInviteService) {
        this.clientPortalInviteService = clientPortalInviteService;
    }

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody CreatePortalInviteRequest request) {
        clientPortalInviteService.createInvite(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/status")
    public ResponseEntity<PortalInviteStatusResponse> status(@RequestParam UUID clientId) {
        return ResponseEntity.ok(clientPortalInviteService.status(clientId));
    }

    @DeleteMapping
    public ResponseEntity<Void> revoke(@RequestParam UUID clientId) {
        clientPortalInviteService.revokeAccess(clientId);
        return ResponseEntity.noContent().build();
    }
}
