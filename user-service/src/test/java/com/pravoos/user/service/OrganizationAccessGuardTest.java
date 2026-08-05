package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.collaboration.internal.service.OrganizationAccessGuard;
import com.pravoos.user.shared.exception.NotOrganizationMemberException;
import com.pravoos.user.shared.exception.OrganizationAccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationAccessGuardTest {

  @Mock private OrganizationMembershipRepository membershipRepository;

  private OrganizationAccessGuard guard;

  @BeforeEach
  void setUp() {
    guard = new OrganizationAccessGuard(membershipRepository);
  }

  @Test
  void requireMember_returnsMembership_whenExists() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    OrganizationMembership membership = membership(orgId, userId, OrgRole.MEMBER);
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId))
        .thenReturn(Optional.of(membership));

    assertThat(guard.requireMember(orgId, userId)).isSameAs(membership);
  }

  @Test
  void requireMember_throws_whenNotMember() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> guard.requireMember(orgId, userId))
        .isInstanceOf(NotOrganizationMemberException.class);
  }

  @Test
  void requireManagerOrOwner_returnsMembership_forManager() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    OrganizationMembership membership = membership(orgId, userId, OrgRole.MANAGER);
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId))
        .thenReturn(Optional.of(membership));

    assertThat(guard.requireManagerOrOwner(orgId, userId)).isSameAs(membership);
  }

  @Test
  void requireManagerOrOwner_returnsMembership_forOwner() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    OrganizationMembership membership = membership(orgId, userId, OrgRole.OWNER);
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId))
        .thenReturn(Optional.of(membership));

    assertThat(guard.requireManagerOrOwner(orgId, userId)).isSameAs(membership);
  }

  @Test
  void requireManagerOrOwner_throws_forPlainMember() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId))
        .thenReturn(Optional.of(membership(orgId, userId, OrgRole.MEMBER)));

    assertThatThrownBy(() -> guard.requireManagerOrOwner(orgId, userId))
        .isInstanceOf(OrganizationAccessDeniedException.class);
  }

  @Test
  void requireManagerOrOwner_throws_whenNotMember() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> guard.requireManagerOrOwner(orgId, userId))
        .isInstanceOf(NotOrganizationMemberException.class);
  }

  @Test
  void requireOwner_returnsMembership_forOwner() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    OrganizationMembership membership = membership(orgId, userId, OrgRole.OWNER);
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId))
        .thenReturn(Optional.of(membership));

    assertThat(guard.requireOwner(orgId, userId)).isSameAs(membership);
  }

  @Test
  void requireOwner_throws_forManager() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId))
        .thenReturn(Optional.of(membership(orgId, userId, OrgRole.MANAGER)));

    assertThatThrownBy(() -> guard.requireOwner(orgId, userId))
        .isInstanceOf(OrganizationAccessDeniedException.class);
  }

  @Test
  void requireOwner_throws_forPlainMember() {
    UUID orgId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(membershipRepository.findByOrgIdAndUserId(orgId, userId))
        .thenReturn(Optional.of(membership(orgId, userId, OrgRole.MEMBER)));

    assertThatThrownBy(() -> guard.requireOwner(orgId, userId))
        .isInstanceOf(OrganizationAccessDeniedException.class);
  }

  private OrganizationMembership membership(UUID orgId, UUID userId, OrgRole role) {
    OrganizationMembership membership = new OrganizationMembership();
    membership.setOrgId(orgId);
    membership.setUserId(userId);
    membership.setOrgRole(role);
    return membership;
  }
}
