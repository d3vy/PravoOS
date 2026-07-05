package com.pravoos.user.repository;

import com.pravoos.user.model.entity.ClientPortalInvite;
import com.pravoos.user.model.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientPortalInviteRepository extends JpaRepository<ClientPortalInvite, UUID> {

    Optional<ClientPortalInvite> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE ClientPortalInvite i SET i.status = com.pravoos.user.model.enums.InviteStatus.REVOKED "
            + "WHERE i.clientId = :clientId AND i.status = com.pravoos.user.model.enums.InviteStatus.PENDING")
    void revokePendingByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT DISTINCT i.userId FROM ClientPortalInvite i "
            + "WHERE i.clientId = :clientId AND i.userId IS NOT NULL "
            + "AND i.status = com.pravoos.user.model.enums.InviteStatus.ACCEPTED")
    List<UUID> findAcceptedUserIdsByClientId(@Param("clientId") UUID clientId);

    @Modifying
    @Query("UPDATE ClientPortalInvite i SET i.status = com.pravoos.user.model.enums.InviteStatus.REVOKED "
            + "WHERE i.clientId = :clientId AND i.status = com.pravoos.user.model.enums.InviteStatus.ACCEPTED")
    int revokeAcceptedByClientId(@Param("clientId") UUID clientId);

    @Query("SELECT i.clientId FROM ClientPortalInvite i "
            + "WHERE i.userId = :userId AND i.status = com.pravoos.user.model.enums.InviteStatus.ACCEPTED")
    List<UUID> findAcceptedClientIdsByUserId(@Param("userId") UUID userId);

    boolean existsByClientIdAndStatus(UUID clientId, InviteStatus status);

    Optional<ClientPortalInvite> findFirstByClientIdAndStatusOrderByCreatedAtDesc(UUID clientId, InviteStatus status);

    int deleteByStatusNotAndExpiresAtBefore(InviteStatus status, LocalDateTime cutoff);
}
