package com.pravoos.user.identity.internal.repository;

import com.pravoos.user.identity.internal.model.entity.PasswordResetToken;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :now "
          + "WHERE t.userId = :userId AND t.usedAt IS NULL AND t.expiresAt > :now")
  int invalidateActiveByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE PasswordResetToken t SET t.usedAt = :now "
          + "WHERE t.id = :id AND t.usedAt IS NULL AND t.expiresAt > :now")
  int consume(@Param("id") UUID id, @Param("now") LocalDateTime now);

  int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
