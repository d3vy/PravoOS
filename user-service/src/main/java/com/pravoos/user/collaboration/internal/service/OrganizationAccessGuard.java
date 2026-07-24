package com.pravoos.user.collaboration.internal.service;

import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.shared.exception.NotOrganizationMemberException;
import com.pravoos.user.shared.exception.OrganizationAccessDeniedException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrganizationAccessGuard {

  private final OrganizationMembershipRepository membershipRepository;

  public OrganizationAccessGuard(OrganizationMembershipRepository membershipRepository) {
    this.membershipRepository = membershipRepository;
  }

  public OrganizationMembership requireMember(UUID orgId, UUID userId) {
    return membershipRepository
        .findByOrgIdAndUserId(orgId, userId)
        .orElseThrow(NotOrganizationMemberException::new);
  }

  public OrganizationMembership requireManagerOrOwner(UUID orgId, UUID userId) {
    OrganizationMembership membership = requireMember(orgId, userId);
    if (membership.getOrgRole() == OrgRole.MEMBER) {
      throw new OrganizationAccessDeniedException();
    }
    return membership;
  }

  public OrganizationMembership requireOwner(UUID orgId, UUID userId) {
    OrganizationMembership membership = requireMember(orgId, userId);
    if (membership.getOrgRole() != OrgRole.OWNER) {
      throw new OrganizationAccessDeniedException();
    }
    return membership;
  }
}
