package com.pravoos.user.repository;

import com.pravoos.user.model.entity.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    List<OrganizationMembership> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<OrganizationMembership> findByOrgIdAndUserId(UUID orgId, UUID userId);

    boolean existsByOrgIdAndUserId(UUID orgId, UUID userId);

    List<OrganizationMembership> findByOrgIdOrderByCreatedAtAsc(UUID orgId);

    long countByOrgId(UUID orgId);
}
