package com.pravoos.user.service;

import com.pravoos.user.collaboration.internal.dto.CreateOrganizationRequest;
import com.pravoos.user.collaboration.internal.dto.OrganizationResponse;
import com.pravoos.user.collaboration.internal.model.entity.Organization;
import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.collaboration.internal.repository.OrganizationRepository;
import com.pravoos.user.collaboration.internal.service.OrganizationAccessGuard;
import com.pravoos.user.collaboration.internal.service.OrganizationService;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.exception.NotOrganizationMemberException;
import com.pravoos.user.shared.exception.OrganizationAccessDeniedException;
import com.pravoos.user.shared.exception.PravoosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, membershipRepository, userRepository,
                new OrganizationAccessGuard(membershipRepository));
    }

    @Test
    void createMakesCallerOwner() {
        UUID lawyerId = UUID.randomUUID();
        when(userRepository.findById(lawyerId)).thenReturn(Optional.of(lawyer(lawyerId)));
        when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationResponse response = service.create(lawyerId, new CreateOrganizationRequest("Фирма"));

        assertThat(response.myRole()).isEqualTo(OrgRole.OWNER);
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.ownerId()).isEqualTo(lawyerId);
    }

    @Test
    void createRejectedForNonLawyer() {
        UUID adminId = UUID.randomUUID();
        User admin = lawyer(adminId);
        admin.setRole(UserRole.ADMIN);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.create(adminId, new CreateOrganizationRequest("Фирма")))
                .isInstanceOf(PravoosException.class);
    }

    @Test
    void listMembersRejectedForNonMember() {
        UUID orgId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        when(membershipRepository.findByOrgIdAndUserId(orgId, stranger)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembers(stranger, orgId))
                .isInstanceOf(NotOrganizationMemberException.class);
    }

    @Test
    void ownerCannotLeaveOwnOrganization() {
        UUID orgId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Organization org = new Organization();
        org.setOwnerId(ownerId);
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        assertThatThrownBy(() -> service.leave(ownerId, orgId))
                .isInstanceOf(PravoosException.class);
    }

    @Test
    void managerCannotRemoveAnotherManager() {
        UUID orgId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(membershipRepository.findByOrgIdAndUserId(orgId, managerId))
                .thenReturn(Optional.of(membership(orgId, managerId, OrgRole.MANAGER)));
        Organization org = new Organization();
        org.setOwnerId(UUID.randomUUID());
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(membershipRepository.findByOrgIdAndUserId(orgId, targetId))
                .thenReturn(Optional.of(membership(orgId, targetId, OrgRole.MANAGER)));

        assertThatThrownBy(() -> service.removeMember(managerId, orgId, targetId))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    private User lawyer(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("lawyer@example.com");
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
