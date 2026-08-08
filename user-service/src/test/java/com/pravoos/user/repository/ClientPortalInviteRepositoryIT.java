package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.collaboration.internal.model.entity.ClientPortalInvite;
import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import com.pravoos.user.collaboration.internal.repository.ClientPortalInviteRepository;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
class ClientPortalInviteRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private ClientPortalInviteRepository clientPortalInviteRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;

  private final AtomicInteger emailSeq = new AtomicInteger();

  private UUID persistUser(UserRole role) {
    User user = new User();
    user.setEmail("portal-user-" + emailSeq.incrementAndGet() + "@example.com");
    user.setPasswordHash("hash");
    user.setRole(role);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.saveAndFlush(user).getId();
  }

  private ClientPortalInvite persistInvite(
      UUID clientId, String tokenHash, InviteStatus status, UUID userId) {
    ClientPortalInvite invite = new ClientPortalInvite();
    invite.setClientId(clientId);
    invite.setLawyerId(persistUser(UserRole.LAWYER));
    invite.setEmail("client@example.com");
    invite.setTokenHash(tokenHash);
    invite.setStatus(status);
    invite.setUserId(userId);
    invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(7));
    return clientPortalInviteRepository.saveAndFlush(invite);
  }

  @Test
  void findByTokenHashReturnsMatchingInvite() {
    persistInvite(UUID.randomUUID(), "hash-1", InviteStatus.PENDING, null);

    assertThat(clientPortalInviteRepository.findByTokenHash("hash-1")).isPresent();
    assertThat(clientPortalInviteRepository.findByTokenHash("missing")).isEmpty();
  }

  @Test
  void revokePendingByClientIdOnlyRevokesPendingInvitesForThatClient() {
    UUID clientId = UUID.randomUUID();
    ClientPortalInvite pending =
        persistInvite(clientId, "hash-pending", InviteStatus.PENDING, null);
    ClientPortalInvite accepted =
        persistInvite(
            clientId, "hash-accepted", InviteStatus.ACCEPTED, persistUser(UserRole.CLIENT));
    ClientPortalInvite otherClientPending =
        persistInvite(UUID.randomUUID(), "hash-other", InviteStatus.PENDING, null);

    clientPortalInviteRepository.revokePendingByClientId(clientId);
    entityManager.clear();

    assertThat(clientPortalInviteRepository.findById(pending.getId()).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.REVOKED);
    assertThat(clientPortalInviteRepository.findById(accepted.getId()).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.ACCEPTED);
    assertThat(
            clientPortalInviteRepository
                .findById(otherClientPending.getId())
                .orElseThrow()
                .getStatus())
        .isEqualTo(InviteStatus.PENDING);
  }

  @Test
  void findAcceptedUserIdsByClientIdReturnsOnlyAcceptedWithUserId() {
    UUID clientId = UUID.randomUUID();
    UUID acceptedUserId = persistUser(UserRole.CLIENT);
    persistInvite(clientId, "hash-accepted", InviteStatus.ACCEPTED, acceptedUserId);
    persistInvite(clientId, "hash-pending", InviteStatus.PENDING, null);

    assertThat(clientPortalInviteRepository.findAcceptedUserIdsByClientId(clientId))
        .containsExactly(acceptedUserId);
  }

  @Test
  void revokeAcceptedByClientIdRevokesOnlyAcceptedInvitesAndReturnsCount() {
    UUID clientId = UUID.randomUUID();
    ClientPortalInvite accepted =
        persistInvite(
            clientId, "hash-accepted", InviteStatus.ACCEPTED, persistUser(UserRole.CLIENT));
    persistInvite(clientId, "hash-pending", InviteStatus.PENDING, null);

    int revoked = clientPortalInviteRepository.revokeAcceptedByClientId(clientId);
    entityManager.clear();

    assertThat(revoked).isEqualTo(1);
    assertThat(clientPortalInviteRepository.findById(accepted.getId()).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.REVOKED);
  }

  @Test
  void findAcceptedClientIdsByUserIdReturnsClientsForAcceptedInvitesOnly() {
    UUID userId = persistUser(UserRole.CLIENT);
    UUID clientId = UUID.randomUUID();
    persistInvite(clientId, "hash-accepted", InviteStatus.ACCEPTED, userId);
    persistInvite(UUID.randomUUID(), "hash-pending", InviteStatus.PENDING, userId);

    assertThat(clientPortalInviteRepository.findAcceptedClientIdsByUserId(userId))
        .containsExactly(clientId);
  }

  @Test
  void existsByClientIdAndStatusReflectsPersistedState() {
    UUID clientId = UUID.randomUUID();
    persistInvite(clientId, "hash-pending", InviteStatus.PENDING, null);

    assertThat(
            clientPortalInviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.PENDING))
        .isTrue();
    assertThat(
            clientPortalInviteRepository.existsByClientIdAndStatus(clientId, InviteStatus.ACCEPTED))
        .isFalse();
  }

  @Test
  void findFirstByClientIdAndStatusOrderByCreatedAtDescReturnsMostRecent() {
    UUID clientId = UUID.randomUUID();
    ClientPortalInvite older = persistInvite(clientId, "hash-older", InviteStatus.PENDING, null);
    older.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
    clientPortalInviteRepository.saveAndFlush(older);

    ClientPortalInvite newer = persistInvite(clientId, "hash-newer", InviteStatus.PENDING, null);

    assertThat(
            clientPortalInviteRepository
                .findFirstByClientIdAndStatusOrderByCreatedAtDesc(clientId, InviteStatus.PENDING)
                .orElseThrow()
                .getTokenHash())
        .isIn("hash-older", "hash-newer");
    assertThat(newer.getCreatedAt()).isNotNull();
  }

  @Test
  void deleteByStatusNotAndExpiresAtBeforeRemovesExpiredNonAcceptedInvites() {
    ClientPortalInvite expiredPending =
        persistInvite(UUID.randomUUID(), "hash-expired-pending", InviteStatus.PENDING, null);
    expiredPending.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    clientPortalInviteRepository.saveAndFlush(expiredPending);

    ClientPortalInvite expiredAccepted =
        persistInvite(
            UUID.randomUUID(),
            "hash-expired-accepted",
            InviteStatus.ACCEPTED,
            persistUser(UserRole.CLIENT));
    expiredAccepted.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    clientPortalInviteRepository.saveAndFlush(expiredAccepted);

    ClientPortalInvite freshPending =
        persistInvite(UUID.randomUUID(), "hash-fresh-pending", InviteStatus.PENDING, null);

    int deleted =
        clientPortalInviteRepository.deleteByStatusNotAndExpiresAtBefore(
            InviteStatus.ACCEPTED, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(deleted).isEqualTo(1);
    assertThat(clientPortalInviteRepository.findById(expiredPending.getId())).isEmpty();
    assertThat(clientPortalInviteRepository.findById(expiredAccepted.getId())).isPresent();
    assertThat(clientPortalInviteRepository.findById(freshPending.getId())).isPresent();
  }
}
