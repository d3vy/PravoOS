package com.pravoos.user.service;

import com.pravoos.user.identity.internal.service.EmailRateLimiter;
import com.pravoos.user.shared.exception.InvalidInviteException;
import com.pravoos.user.shared.exception.OrganizationAccessDeniedException;
import com.pravoos.user.model.dto.CreateInviteRequest;
import com.pravoos.user.model.dto.OrganizationResponse;
import com.pravoos.user.model.entity.Organization;
import com.pravoos.user.model.entity.OrganizationInvite;
import com.pravoos.user.model.entity.OrganizationMembership;
import com.pravoos.user.identity.internal.model.entity.User;
import com.pravoos.user.model.enums.InviteStatus;
import com.pravoos.user.model.enums.OrgRole;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.repository.OrganizationInviteRepository;
import com.pravoos.user.repository.OrganizationMembershipRepository;
import com.pravoos.user.repository.OrganizationRepository;
import com.pravoos.user.identity.internal.repository.UserRepository;
import com.pravoos.user.identity.internal.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationInviteServiceTest {

    @Mock private OrganizationInviteRepository inviteRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailRateLimiter emailRateLimiter;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final TokenHasher tokenHasher = new TokenHasher();
    private OrganizationInviteService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationInviteService(inviteRepository, membershipRepository, organizationRepository,
                userRepository, new OrganizationAccessGuard(membershipRepository), tokenHasher,
                emailRateLimiter, eventPublisher);
    }

    @Test
    void cannotInviteAsOwner() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(membershipRepository.findByOrgIdAndUserId(orgId, callerId))
                .thenReturn(Optional.of(membership(orgId, callerId, OrgRole.OWNER)));

        assertThatThrownBy(() -> service.invite(callerId, orgId, new CreateInviteRequest("new@example.com", OrgRole.OWNER)))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void managerCannotGrantManagerRole() {
        UUID orgId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(membershipRepository.findByOrgIdAndUserId(orgId, callerId))
                .thenReturn(Optional.of(membership(orgId, callerId, OrgRole.MANAGER)));

        assertThatThrownBy(() -> service.invite(callerId, orgId, new CreateInviteRequest("new@example.com", OrgRole.MANAGER)))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void acceptRejectsEmailMismatch() {
        UUID userId = UUID.randomUUID();
        String rawToken = "raw-token";
        OrganizationInvite invite = pendingInvite(UUID.randomUUID(), "invited@example.com", rawToken);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.findById(userId)).thenReturn(Optional.of(lawyer(userId, "other@example.com")));

        assertThatThrownBy(() -> service.accept(userId, rawToken))
                .isInstanceOf(InvalidInviteException.class);
    }

    @Test
    void acceptCreatesMembershipOnMatch() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String rawToken = "raw-token";
        OrganizationInvite invite = pendingInvite(orgId, "member@example.com", rawToken);
        when(inviteRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))).thenReturn(Optional.of(invite));
        when(userRepository.findById(userId)).thenReturn(Optional.of(lawyer(userId, "Member@Example.com")));
        when(membershipRepository.existsByOrgIdAndUserId(orgId, userId)).thenReturn(false);
        Organization org = new Organization();
        org.setName("Фирма");
        org.setOwnerId(UUID.randomUUID());
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(membershipRepository.countByOrgId(any())).thenReturn(2L);

        OrganizationResponse response = service.accept(userId, rawToken);

        assertThat(response.myRole()).isEqualTo(OrgRole.MEMBER);
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    private OrganizationInvite pendingInvite(UUID orgId, String email, String rawToken) {
        OrganizationInvite invite = new OrganizationInvite();
        invite.setOrgId(orgId);
        invite.setEmail(email);
        invite.setOrgRole(OrgRole.MEMBER);
        invite.setTokenHash(tokenHasher.sha256Hex(rawToken));
        invite.setInvitedBy(UUID.randomUUID());
        invite.setStatus(InviteStatus.PENDING);
        invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
        return invite;
    }

    private User lawyer(UUID id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(UserRole.LAWYER);
        return user;
    }

    private OrganizationMembership membership(UUID orgId, UUID userId, OrgRole role) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrgId(orgId);
        membership.setUserId(userId);
        membership.setOrgRole(role);
        return membership;
    }
}
