package com.pravoos.user.collaboration.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.collaboration.internal.model.entity.Organization;
import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.collaboration.internal.repository.OrganizationRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LawyerMembershipCleanupImplTest {

  @Mock private OrganizationMembershipRepository membershipRepository;
  @Mock private OrganizationRepository organizationRepository;

  private LawyerMembershipCleanupImpl cleanup;

  @BeforeEach
  void setUp() {
    cleanup = new LawyerMembershipCleanupImpl(membershipRepository, organizationRepository);
  }

  @Test
  void purgeMembershipsForDeletedLawyer_returnsEmptyMapWhenNoMemberships() {
    UUID userId = UUID.randomUUID();
    when(membershipRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

    Map<UUID, UUID> result = cleanup.purgeMembershipsForDeletedLawyer(userId);

    assertThat(result).isEmpty();
    verify(membershipRepository).deleteAll(List.of());
  }

  @Test
  void purgeMembershipsForDeletedLawyer_skipsOrgsWhereLawyerIsOwner() {
    UUID userId = UUID.randomUUID();
    Organization ownedOrg = organization(userId);
    OrganizationMembership membership = membership(ownedOrg.getId(), userId);
    when(membershipRepository.findByUserIdOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(membership));
    when(organizationRepository.findById(ownedOrg.getId())).thenReturn(Optional.of(ownedOrg));

    Map<UUID, UUID> result = cleanup.purgeMembershipsForDeletedLawyer(userId);

    assertThat(result).isEmpty();
    verify(membershipRepository).deleteAll(List.of(membership));
  }

  @Test
  void purgeMembershipsForDeletedLawyer_collectsOwnerIdForOrgsWhereLawyerIsMember() {
    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Organization org = organization(ownerId);
    OrganizationMembership membership = membership(org.getId(), userId);
    when(membershipRepository.findByUserIdOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(membership));
    when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));

    Map<UUID, UUID> result = cleanup.purgeMembershipsForDeletedLawyer(userId);

    assertThat(result).containsExactly(Map.entry(org.getId(), ownerId));
    verify(membershipRepository).deleteAll(List.of(membership));
  }

  @Test
  void purgeMembershipsForDeletedLawyer_skipsOrgsThatNoLongerExist() {
    UUID userId = UUID.randomUUID();
    OrganizationMembership membership = membership(UUID.randomUUID(), userId);
    when(membershipRepository.findByUserIdOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(membership));
    when(organizationRepository.findById(membership.getOrgId())).thenReturn(Optional.empty());

    Map<UUID, UUID> result = cleanup.purgeMembershipsForDeletedLawyer(userId);

    assertThat(result).isEmpty();
    verify(membershipRepository).deleteAll(List.of(membership));
  }

  private static Organization organization(UUID ownerId) {
    Organization org = new Organization();
    setId(org, UUID.randomUUID());
    org.setOwnerId(ownerId);
    org.setName("org");
    return org;
  }

  private static OrganizationMembership membership(UUID orgId, UUID userId) {
    OrganizationMembership membership = new OrganizationMembership();
    setId(membership, UUID.randomUUID());
    membership.setOrgId(orgId);
    membership.setUserId(userId);
    membership.setOrgRole(OrgRole.MEMBER);
    return membership;
  }

  private static void setId(Object entity, UUID id) {
    try {
      var field = entity.getClass().getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
