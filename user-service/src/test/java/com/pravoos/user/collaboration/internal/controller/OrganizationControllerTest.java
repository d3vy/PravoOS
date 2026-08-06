package com.pravoos.user.collaboration.internal.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.user.collaboration.internal.dto.AcceptInviteRequest;
import com.pravoos.user.collaboration.internal.dto.ChangeMemberRoleRequest;
import com.pravoos.user.collaboration.internal.dto.CreateInviteRequest;
import com.pravoos.user.collaboration.internal.dto.CreateOrganizationRequest;
import com.pravoos.user.collaboration.internal.dto.InviteResponse;
import com.pravoos.user.collaboration.internal.dto.OrganizationMemberResponse;
import com.pravoos.user.collaboration.internal.dto.OrganizationResponse;
import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.collaboration.internal.service.OrganizationInviteService;
import com.pravoos.user.collaboration.internal.service.OrganizationService;
import com.pravoos.user.shared.exception.GlobalExceptionHandler;
import com.pravoos.user.shared.exception.NotOrganizationMemberException;
import com.pravoos.user.shared.exception.OrganizationAccessDeniedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

  @Mock private OrganizationService organizationService;
  @Mock private OrganizationInviteService inviteService;
  @Mock private Authentication authentication;

  private MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UUID userId = UUID.randomUUID();
  private final UUID orgId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    OrganizationController controller =
        new OrganizationController(organizationService, inviteService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createReturns201WithCreatedOrganization() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    OrganizationResponse response =
        new OrganizationResponse(orgId, "ACME", userId, OrgRole.OWNER, 1, LocalDateTime.now());
    when(organizationService.create(userId, new CreateOrganizationRequest("ACME")))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/api/user/org")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest("ACME"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("ACME"));
  }

  @Test
  void createReturns400ForBlankName() throws Exception {
    mockMvc
        .perform(
            post("/api/user/org")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateOrganizationRequest(""))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listMyOrganizationsReturnsList() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(organizationService.listMyOrganizations(userId))
        .thenReturn(
            List.of(
                new OrganizationResponse(
                    orgId, "ACME", userId, OrgRole.OWNER, 1, LocalDateTime.now())));

    mockMvc
        .perform(get("/api/user/org").principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(orgId.toString()));
  }

  @Test
  void listMembersReturns403WhenNotOrgMember() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(organizationService.listMembers(userId, orgId))
        .thenThrow(new NotOrganizationMemberException());

    mockMvc
        .perform(get("/api/user/org/{orgId}/members", orgId).principal(authentication))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NOT_ORG_MEMBER"));
  }

  @Test
  void listMembersReturnsMembersList() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(organizationService.listMembers(userId, orgId))
        .thenReturn(
            List.of(
                new OrganizationMemberResponse(
                    userId, "u@b.com", "U B", OrgRole.MEMBER, LocalDateTime.now())));

    mockMvc
        .perform(get("/api/user/org/{orgId}/members", orgId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(userId.toString()));
  }

  @Test
  void changeMemberRoleDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID targetUserId = UUID.randomUUID();
    when(organizationService.changeMemberRole(userId, orgId, targetUserId, OrgRole.MANAGER))
        .thenReturn(
            new OrganizationMemberResponse(
                targetUserId, "u@b.com", "U B", OrgRole.MANAGER, LocalDateTime.now()));

    mockMvc
        .perform(
            patch("/api/user/org/{orgId}/members/{userId}/role", orgId, targetUserId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new ChangeMemberRoleRequest(OrgRole.MANAGER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgRole").value("MANAGER"));
  }

  @Test
  void changeMemberRoleReturns403WhenInsufficientRights() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID targetUserId = UUID.randomUUID();
    when(organizationService.changeMemberRole(userId, orgId, targetUserId, OrgRole.OWNER))
        .thenThrow(new OrganizationAccessDeniedException());

    mockMvc
        .perform(
            patch("/api/user/org/{orgId}/members/{userId}/role", orgId, targetUserId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new ChangeMemberRoleRequest(OrgRole.OWNER))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ORG_ACCESS_DENIED"));
  }

  @Test
  void removeMemberReturns204AndDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID targetUserId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/user/org/{orgId}/members/{userId}", orgId, targetUserId)
                .principal(authentication))
        .andExpect(status().isNoContent());

    verify(organizationService).removeMember(userId, orgId, targetUserId);
  }

  @Test
  void leaveReturns204AndDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());

    mockMvc
        .perform(post("/api/user/org/{orgId}/leave", orgId).principal(authentication))
        .andExpect(status().isNoContent());

    verify(organizationService).leave(userId, orgId);
  }

  @Test
  void inviteReturns201WithCreatedInvite() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID inviteId = UUID.randomUUID();
    CreateInviteRequest request = new CreateInviteRequest("new@example.com", OrgRole.MEMBER);
    when(inviteService.invite(userId, orgId, request))
        .thenReturn(
            new InviteResponse(
                inviteId,
                "new@example.com",
                OrgRole.MEMBER,
                InviteStatus.PENDING,
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now()));

    mockMvc
        .perform(
            post("/api/user/org/{orgId}/invites", orgId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("new@example.com"));
  }

  @Test
  void inviteReturns400ForInvalidEmail() throws Exception {
    mockMvc
        .perform(
            post("/api/user/org/{orgId}/invites", orgId)
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateInviteRequest("not-an-email", OrgRole.MEMBER))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listInvitesReturnsPendingInvites() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID inviteId = UUID.randomUUID();
    when(inviteService.listPending(userId, orgId))
        .thenReturn(
            List.of(
                new InviteResponse(
                    inviteId,
                    "new@example.com",
                    OrgRole.MEMBER,
                    InviteStatus.PENDING,
                    LocalDateTime.now().plusDays(7),
                    LocalDateTime.now())));

    mockMvc
        .perform(get("/api/user/org/{orgId}/invites", orgId).principal(authentication))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(inviteId.toString()));
  }

  @Test
  void revokeInviteReturns204AndDelegatesToService() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    UUID inviteId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/user/org/{orgId}/invites/{inviteId}", orgId, inviteId)
                .principal(authentication))
        .andExpect(status().isNoContent());

    verify(inviteService).revoke(userId, orgId, inviteId);
  }

  @Test
  void acceptInviteReturnsJoinedOrganization() throws Exception {
    when(authentication.getPrincipal()).thenReturn(userId.toString());
    when(inviteService.accept(userId, "invite-token"))
        .thenReturn(
            new OrganizationResponse(
                orgId, "ACME", userId, OrgRole.MEMBER, 2, LocalDateTime.now()));

    mockMvc
        .perform(
            post("/api/user/org/invites/accept")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AcceptInviteRequest("invite-token"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orgId.toString()));
  }

  @Test
  void acceptInviteReturns400ForBlankToken() throws Exception {
    mockMvc
        .perform(
            post("/api/user/org/invites/accept")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AcceptInviteRequest(""))))
        .andExpect(status().isBadRequest());
  }
}
