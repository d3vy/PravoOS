package com.pravoos.user.collaboration.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrgMembershipProviderImplTest {

  @Mock private OrganizationMembershipRepository membershipRepository;

  private OrgMembershipProviderImpl provider;

  @BeforeEach
  void setUp() {
    provider = new OrgMembershipProviderImpl(membershipRepository);
  }

  @Test
  void orgIdsForUser_returnsEmptyListWhenNoMemberships() {
    UUID userId = UUID.randomUUID();
    when(membershipRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

    List<UUID> result = provider.orgIdsForUser(userId);

    assertThat(result).isEmpty();
  }

  @Test
  void orgIdsForUser_mapsMembershipsToOrgIdsInOrder() {
    UUID userId = UUID.randomUUID();
    OrganizationMembership first = membership(UUID.randomUUID(), userId);
    OrganizationMembership second = membership(UUID.randomUUID(), userId);
    when(membershipRepository.findByUserIdOrderByCreatedAtAsc(userId))
        .thenReturn(List.of(first, second));

    List<UUID> result = provider.orgIdsForUser(userId);

    assertThat(result).containsExactly(first.getOrgId(), second.getOrgId());
  }

  private static OrganizationMembership membership(UUID orgId, UUID userId) {
    OrganizationMembership membership = new OrganizationMembership();
    membership.setOrgId(orgId);
    membership.setUserId(userId);
    return membership;
  }
}
