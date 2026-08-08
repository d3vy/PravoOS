package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.identity.internal.model.entity.PasswordResetToken;
import com.pravoos.user.identity.internal.repository.PasswordResetTokenRepository;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PasswordResetTokenRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired private UserRepository userRepository;

  private User persistUser(String email) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hash");
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.saveAndFlush(user);
  }

  private PasswordResetToken persistToken(
      UUID userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime usedAt) {
    PasswordResetToken token = new PasswordResetToken();
    token.setUserId(userId);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(expiresAt);
    token.setUsedAt(usedAt);
    return passwordResetTokenRepository.saveAndFlush(token);
  }

  @Test
  void findByTokenHashReturnsMatchingToken() {
    User user = persistUser("reset-owner@example.com");
    persistToken(user.getId(), "hash-1", LocalDateTime.now(ZoneOffset.UTC).plusHours(1), null);

    assertThat(passwordResetTokenRepository.findByTokenHash("hash-1")).isPresent();
    assertThat(passwordResetTokenRepository.findByTokenHash("missing-hash")).isEmpty();
  }

  @Test
  void invalidateActiveByUserIdOnlyMarksActiveTokensAsUsed() {
    User user = persistUser("invalidate@example.com");
    User otherUser = persistUser("other@example.com");
    persistToken(
        user.getId(), "hash-active-1", LocalDateTime.now(ZoneOffset.UTC).plusHours(1), null);
    persistToken(
        user.getId(), "hash-active-2", LocalDateTime.now(ZoneOffset.UTC).plusHours(1), null);
    persistToken(
        user.getId(),
        "hash-already-used",
        LocalDateTime.now(ZoneOffset.UTC).plusHours(1),
        LocalDateTime.now(ZoneOffset.UTC));
    persistToken(
        user.getId(), "hash-expired", LocalDateTime.now(ZoneOffset.UTC).minusHours(1), null);
    persistToken(
        otherUser.getId(), "hash-other-user", LocalDateTime.now(ZoneOffset.UTC).plusHours(1), null);

    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    int updated = passwordResetTokenRepository.invalidateActiveByUserId(user.getId(), now);

    assertThat(updated).isEqualTo(2);
    assertThat(
            passwordResetTokenRepository.findByTokenHash("hash-active-1").orElseThrow().getUsedAt())
        .isNotNull();
    assertThat(
            passwordResetTokenRepository.findByTokenHash("hash-active-2").orElseThrow().getUsedAt())
        .isNotNull();
    assertThat(
            passwordResetTokenRepository.findByTokenHash("hash-expired").orElseThrow().getUsedAt())
        .isNull();
    assertThat(
            passwordResetTokenRepository
                .findByTokenHash("hash-other-user")
                .orElseThrow()
                .getUsedAt())
        .isNull();
  }

  @Test
  void deleteByExpiresAtBeforeRemovesOnlyExpiredTokens() {
    User user = persistUser("cleanup@example.com");
    persistToken(
        user.getId(), "hash-expired-1", LocalDateTime.now(ZoneOffset.UTC).minusDays(2), null);
    persistToken(
        user.getId(), "hash-expired-2", LocalDateTime.now(ZoneOffset.UTC).minusHours(1), null);
    persistToken(
        user.getId(), "hash-not-expired", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);

    int deleted =
        passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));

    assertThat(deleted).isEqualTo(2);
    assertThat(passwordResetTokenRepository.findByTokenHash("hash-not-expired")).isPresent();
    assertThat(passwordResetTokenRepository.findByTokenHash("hash-expired-1")).isEmpty();
    assertThat(passwordResetTokenRepository.findByTokenHash("hash-expired-2")).isEmpty();
  }
}
