package com.pravoos.user.identity.internal.repository;

import com.pravoos.user.identity.internal.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndIpAddressAndRevokedAtIsNullAndExpiresAtAfter(
            UUID userId, String ipAddress, LocalDateTime now);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, LocalDateTime now);

    Optional<RefreshToken> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
