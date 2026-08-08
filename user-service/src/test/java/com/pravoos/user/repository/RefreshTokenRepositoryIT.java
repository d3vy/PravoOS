package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.identity.internal.model.entity.RefreshToken;
import com.pravoos.user.identity.internal.repository.RefreshTokenRepository;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
class RefreshTokenRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;

  private User persistUser(String email) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hash");
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.saveAndFlush(user);
  }

  private RefreshToken persistToken(
      UUID userId, String tokenHash, LocalDateTime expiresAt, LocalDateTime revokedAt) {
    RefreshToken token = new RefreshToken();
    token.setUserId(userId);
    token.setTokenHash(tokenHash);
    token.setExpiresAt(expiresAt);
    token.setRevokedAt(revokedAt);
    return refreshTokenRepository.saveAndFlush(token);
  }

  @Test
  void findByTokenHashReturnsMatchingToken() {
    User user = persistUser("token-owner@example.com");
    persistToken(user.getId(), "hash-1", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);

    assertThat(refreshTokenRepository.findByTokenHash("hash-1")).isPresent();
    assertThat(refreshTokenRepository.findByTokenHash("missing-hash")).isEmpty();
  }

  @Test
  void existsByUserIdAndIpAddressAndRevokedAtIsNullAndExpiresAtAfterMatchesOnlyActiveSession() {
    User user = persistUser("ip-check@example.com");
    RefreshToken active =
        persistToken(
            user.getId(), "hash-active", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);
    active.setIpAddress("1.2.3.4");
    refreshTokenRepository.saveAndFlush(active);

    RefreshToken revoked =
        persistToken(
            user.getId(),
            "hash-revoked",
            LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            LocalDateTime.now(ZoneOffset.UTC));
    revoked.setIpAddress("1.2.3.4");
    refreshTokenRepository.saveAndFlush(revoked);

    boolean existsForActiveIp =
        refreshTokenRepository.existsByUserIdAndIpAddressAndRevokedAtIsNullAndExpiresAtAfter(
            user.getId(), "1.2.3.4", LocalDateTime.now(ZoneOffset.UTC));
    boolean existsForUnknownIp =
        refreshTokenRepository.existsByUserIdAndIpAddressAndRevokedAtIsNullAndExpiresAtAfter(
            user.getId(), "9.9.9.9", LocalDateTime.now(ZoneOffset.UTC));

    assertThat(existsForActiveIp).isTrue();
    assertThat(existsForUnknownIp).isFalse();
  }

  @Test
  void findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDescExcludesInactive() {
    User user = persistUser("sessions@example.com");
    persistToken(
        user.getId(), "hash-expired", LocalDateTime.now(ZoneOffset.UTC).minusDays(1), null);
    persistToken(
        user.getId(),
        "hash-revoked",
        LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
        LocalDateTime.now(ZoneOffset.UTC));
    persistToken(
        user.getId(), "hash-active-older", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);
    RefreshToken activeNewer =
        persistToken(
            user.getId(), "hash-active-newer", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);
    activeNewer.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
    refreshTokenRepository.saveAndFlush(activeNewer);

    List<RefreshToken> active =
        refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            user.getId(), LocalDateTime.now(ZoneOffset.UTC));

    assertThat(active)
        .extracting(RefreshToken::getTokenHash)
        .containsExactly("hash-active-newer", "hash-active-older");
  }

  @Test
  void findByIdAndUserIdReturnsEmptyForOtherUsersToken() {
    User owner = persistUser("owner@example.com");
    User stranger = persistUser("stranger@example.com");
    RefreshToken token =
        persistToken(
            owner.getId(), "hash-own", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);

    assertThat(refreshTokenRepository.findByIdAndUserId(token.getId(), owner.getId())).isPresent();
    assertThat(refreshTokenRepository.findByIdAndUserId(token.getId(), stranger.getId())).isEmpty();
  }

  @Test
  void revokeAllActiveByUserIdOnlyAffectsGivenUsersActiveTokens() {
    User user = persistUser("revoke-all@example.com");
    User otherUser = persistUser("other@example.com");
    persistToken(user.getId(), "hash-a", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);
    persistToken(user.getId(), "hash-b", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);
    RefreshToken alreadyRevoked =
        persistToken(
            user.getId(),
            "hash-c",
            LocalDateTime.now(ZoneOffset.UTC).plusDays(1),
            LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
    persistToken(
        otherUser.getId(), "hash-other", LocalDateTime.now(ZoneOffset.UTC).plusDays(1), null);

    LocalDateTime revokedAt = LocalDateTime.now(ZoneOffset.UTC);
    int updated = refreshTokenRepository.revokeAllActiveByUserId(user.getId(), revokedAt);
    entityManager.clear();

    assertThat(updated).isEqualTo(2);
    assertThat(refreshTokenRepository.findByTokenHash("hash-a").orElseThrow().getRevokedAt())
        .isNotNull();
    assertThat(refreshTokenRepository.findByTokenHash("hash-b").orElseThrow().getRevokedAt())
        .isNotNull();
    assertThat(refreshTokenRepository.findByTokenHash("hash-other").orElseThrow().getRevokedAt())
        .isNull();
    assertThat(refreshTokenRepository.findById(alreadyRevoked.getId()).orElseThrow().getRevokedAt())
        .isNotNull();
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

    int deleted = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));

    assertThat(deleted).isEqualTo(2);
    assertThat(refreshTokenRepository.findByTokenHash("hash-not-expired")).isPresent();
    assertThat(refreshTokenRepository.findByTokenHash("hash-expired-1")).isEmpty();
    assertThat(refreshTokenRepository.findByTokenHash("hash-expired-2")).isEmpty();
  }
}
