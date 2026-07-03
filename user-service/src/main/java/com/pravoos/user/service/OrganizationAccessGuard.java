package com.pravoos.user.service;

import com.pravoos.user.exception.NotOrganizationMemberException;
import com.pravoos.user.exception.OrganizationAccessDeniedException;
import com.pravoos.user.model.entity.OrganizationMembership;
import com.pravoos.user.model.enums.OrgRole;
import com.pravoos.user.repository.OrganizationMembershipRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrganizationAccessGuard {

    private final OrganizationMembershipRepository membershipRepository;

    public OrganizationAccessGuard(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    public OrganizationMembership requireMember(UUID orgId, UUID userId) {
        return membershipRepository.findByOrgIdAndUserId(orgId, userId)
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
