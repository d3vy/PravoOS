package com.pravoos.user.collaboration.internal.repository;

import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationMembershipRepository
    extends JpaRepository<OrganizationMembership, UUID> {

  interface OrgMemberCount {
    UUID getOrgId();

    long getMemberCount();
  }

  List<OrganizationMembership> findByUserIdOrderByCreatedAtAsc(UUID userId);

  Optional<OrganizationMembership> findByOrgIdAndUserId(UUID orgId, UUID userId);

  boolean existsByOrgIdAndUserId(UUID orgId, UUID userId);

  List<OrganizationMembership> findByOrgIdOrderByCreatedAtAsc(UUID orgId);

  long countByOrgId(UUID orgId);

  @Query(
      "SELECT m.orgId AS orgId, COUNT(m) AS memberCount FROM OrganizationMembership m "
          + "WHERE m.orgId IN :orgIds GROUP BY m.orgId")
  List<OrgMemberCount> countMembersByOrgIds(@Param("orgIds") Collection<UUID> orgIds);
}
