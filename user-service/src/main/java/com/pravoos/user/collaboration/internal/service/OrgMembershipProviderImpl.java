package com.pravoos.user.collaboration.internal.service;

import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.identity.api.OrgMembershipProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class OrgMembershipProviderImpl implements OrgMembershipProvider {

    private final OrganizationMembershipRepository membershipRepository;

    public OrgMembershipProviderImpl(OrganizationMembershipRepository membershipRepository) {
        this.membershipRepository = membershipRepository;
    }

    @Override
    public List<UUID> orgIdsForUser(UUID userId) {
        return membershipRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(OrganizationMembership::getOrgId)
                .toList();
    }
}
