package com.pravoos.user.collaboration.internal.service;

import com.pravoos.user.collaboration.api.LawyerMembershipCleanup;
import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.collaboration.internal.repository.OrganizationRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LawyerMembershipCleanupImpl implements LawyerMembershipCleanup {

  private final OrganizationMembershipRepository membershipRepository;
  private final OrganizationRepository organizationRepository;

  public LawyerMembershipCleanupImpl(
      OrganizationMembershipRepository membershipRepository,
      OrganizationRepository organizationRepository) {
    this.membershipRepository = membershipRepository;
    this.organizationRepository = organizationRepository;
  }

  @Override
  @Transactional
  public Map<UUID, UUID> purgeMembershipsForDeletedLawyer(UUID userId) {
    List<OrganizationMembership> memberships =
        membershipRepository.findByUserIdOrderByCreatedAtAsc(userId);
    Map<UUID, UUID> orgCaseOwners = new HashMap<>();
    for (OrganizationMembership membership : memberships) {
      organizationRepository
          .findById(membership.getOrgId())
          .filter(org -> !org.getOwnerId().equals(userId))
          .ifPresent(org -> orgCaseOwners.put(org.getId(), org.getOwnerId()));
    }
    membershipRepository.deleteAll(memberships);
    return orgCaseOwners;
  }
}
