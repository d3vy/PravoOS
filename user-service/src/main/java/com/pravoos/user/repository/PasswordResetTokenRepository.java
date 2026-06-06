package com.pravoos.user.repository;

import com.pravoos.user.model.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now " +
            "WHERE t.userId = :userId AND t.usedAt IS NULL AND t.expiresAt > :now")
    int invalidateActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
