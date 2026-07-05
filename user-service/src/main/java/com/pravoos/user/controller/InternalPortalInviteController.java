package com.pravoos.user.controller;

import com.pravoos.user.model.dto.CreatePortalInviteRequest;
import com.pravoos.user.model.dto.PortalInviteStatusResponse;
import com.pravoos.user.service.ClientPortalInviteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
