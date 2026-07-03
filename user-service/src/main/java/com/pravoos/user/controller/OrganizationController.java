package com.pravoos.user.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.model.dto.AcceptInviteRequest;
import com.pravoos.user.model.dto.ChangeMemberRoleRequest;
import com.pravoos.user.model.dto.CreateInviteRequest;
import com.pravoos.user.model.dto.CreateOrganizationRequest;
import com.pravoos.user.model.dto.InviteResponse;
import com.pravoos.user.model.dto.OrganizationMemberResponse;
import com.pravoos.user.model.dto.OrganizationResponse;
import com.pravoos.user.service.OrganizationInviteService;
import com.pravoos.user.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/org")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OrganizationInviteService inviteService;

    public OrganizationController(OrganizationService organizationService,
                                  OrganizationInviteService inviteService) {
        this.organizationService = organizationService;
        this.inviteService = inviteService;
    }

    @PostMapping
    public ResponseEntity<OrganizationResponse> create(
            @Valid @RequestBody CreateOrganizationRequest request,
            Authentication authentication) {
        OrganizationResponse response = organizationService.create(SecurityUtils.currentUserId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> listMyOrganizations(Authentication authentication) {
        return ResponseEntity.ok(organizationService.listMyOrganizations(SecurityUtils.currentUserId(authentication)));
    }

    @GetMapping("/{orgId}/members")
    public ResponseEntity<List<OrganizationMemberResponse>> listMembers(
            @PathVariable UUID orgId, Authentication authentication) {
        return ResponseEntity.ok(organizationService.listMembers(SecurityUtils.currentUserId(authentication), orgId));
    }

    @PatchMapping("/{orgId}/members/{userId}/role")
    public ResponseEntity<OrganizationMemberResponse> changeMemberRole(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeMemberRoleRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(organizationService.changeMemberRole(
                SecurityUtils.currentUserId(authentication), orgId, userId, request.orgRole()));
    }

    @DeleteMapping("/{orgId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID orgId, @PathVariable UUID userId, Authentication authentication) {
        organizationService.removeMember(SecurityUtils.currentUserId(authentication), orgId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orgId}/leave")
    public ResponseEntity<Void> leave(@PathVariable UUID orgId, Authentication authentication) {
        organizationService.leave(SecurityUtils.currentUserId(authentication), orgId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orgId}/invites")
    public ResponseEntity<InviteResponse> invite(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateInviteRequest request,
            Authentication authentication) {
        InviteResponse response = inviteService.invite(SecurityUtils.currentUserId(authentication), orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orgId}/invites")
    public ResponseEntity<List<InviteResponse>> listInvites(
            @PathVariable UUID orgId, Authentication authentication) {
        return ResponseEntity.ok(inviteService.listPending(SecurityUtils.currentUserId(authentication), orgId));
    }

    @DeleteMapping("/{orgId}/invites/{inviteId}")
    public ResponseEntity<Void> revokeInvite(
            @PathVariable UUID orgId, @PathVariable UUID inviteId, Authentication authentication) {
        inviteService.revoke(SecurityUtils.currentUserId(authentication), orgId, inviteId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invites/accept")
    public ResponseEntity<OrganizationResponse> acceptInvite(
            @Valid @RequestBody AcceptInviteRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(inviteService.accept(SecurityUtils.currentUserId(authentication), request.token()));
    }
}
