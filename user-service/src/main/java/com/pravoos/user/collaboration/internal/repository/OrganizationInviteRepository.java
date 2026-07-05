package com.pravoos.user.collaboration.internal.repository;

import com.pravoos.user.collaboration.internal.model.entity.OrganizationInvite;
import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {

    Optional<OrganizationInvite> findByTokenHash(String tokenHash);

    List<OrganizationInvite> findByOrgIdAndStatusOrderByCreatedAtDesc(UUID orgId, InviteStatus status);

    @Modifying
    @Query("UPDATE OrganizationInvite i SET i.status = com.pravoos.user.collaboration.internal.model.enums.InviteStatus.REVOKED "
            + "WHERE i.orgId = :orgId AND i.email = :email AND i.status = com.pravoos.user.collaboration.internal.model.enums.InviteStatus.PENDING")
    void revokePendingByOrgIdAndEmail(@Param("orgId") UUID orgId, @Param("email") String email);

    int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
