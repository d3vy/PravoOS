package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.collaboration.internal.model.entity.Organization;
import com.pravoos.user.collaboration.internal.model.entity.OrganizationInvite;
import com.pravoos.user.collaboration.internal.model.enums.InviteStatus;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.collaboration.internal.repository.OrganizationInviteRepository;
import com.pravoos.user.collaboration.internal.repository.OrganizationRepository;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
class OrganizationInviteRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private OrganizationInviteRepository organizationInviteRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager entityManager;

  private final AtomicInteger emailSeq = new AtomicInteger();

  private UUID persistUser() {
    User user = new User();
    user.setEmail("org-user-" + emailSeq.incrementAndGet() + "@example.com");
    user.setPasswordHash("hash");
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.saveAndFlush(user).getId();
  }

  private UUID persistOrganization(UUID ownerId) {
    Organization organization = new Organization();
    organization.setName("Org " + emailSeq.incrementAndGet());
    organization.setOwnerId(ownerId);
    return organizationRepository.saveAndFlush(organization).getId();
  }

  private OrganizationInvite persistInvite(
      UUID orgId, String email, String tokenHash, InviteStatus status) {
    OrganizationInvite invite = new OrganizationInvite();
    invite.setOrgId(orgId);
    invite.setEmail(email);
    invite.setOrgRole(OrgRole.MEMBER);
    invite.setTokenHash(tokenHash);
    invite.setInvitedBy(persistUser());
    invite.setStatus(status);
    invite.setExpiresAt(LocalDateTime.now().plusDays(7));
    return organizationInviteRepository.saveAndFlush(invite);
  }

  @Test
  void findByTokenHashReturnsMatchingInvite() {
    persistInvite(
        persistOrganization(persistUser()), "a@example.com", "hash-1", InviteStatus.PENDING);

    assertThat(organizationInviteRepository.findByTokenHash("hash-1")).isPresent();
    assertThat(organizationInviteRepository.findByTokenHash("missing")).isEmpty();
  }

  @Test
  void findByOrgIdAndStatusOrderByCreatedAtDescOrdersNewestFirst() {
    UUID orgId = persistOrganization(persistUser());
    OrganizationInvite older =
        persistInvite(orgId, "older@example.com", "hash-older", InviteStatus.PENDING);
    older.setExpiresAt(LocalDateTime.now().plusDays(1));
    organizationInviteRepository.saveAndFlush(older);

    OrganizationInvite newer =
        persistInvite(orgId, "newer@example.com", "hash-newer", InviteStatus.PENDING);

    persistInvite(orgId, "revoked@example.com", "hash-revoked", InviteStatus.REVOKED);
    persistInvite(
        persistOrganization(persistUser()),
        "other-org@example.com",
        "hash-other-org",
        InviteStatus.PENDING);

    assertThat(
            organizationInviteRepository.findByOrgIdAndStatusOrderByCreatedAtDesc(
                orgId, InviteStatus.PENDING))
        .extracting(OrganizationInvite::getTokenHash)
        .containsExactly("hash-newer", "hash-older");
  }

  @Test
  void revokePendingByOrgIdAndEmailOnlyRevokesMatchingPendingInvite() {
    UUID orgId = persistOrganization(persistUser());
    OrganizationInvite pending =
        persistInvite(orgId, "target@example.com", "hash-pending", InviteStatus.PENDING);
    OrganizationInvite otherEmail =
        persistInvite(orgId, "other@example.com", "hash-other-email", InviteStatus.PENDING);

    organizationInviteRepository.revokePendingByOrgIdAndEmail(orgId, "target@example.com");
    entityManager.clear();

    assertThat(organizationInviteRepository.findById(pending.getId()).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.REVOKED);
    assertThat(organizationInviteRepository.findById(otherEmail.getId()).orElseThrow().getStatus())
        .isEqualTo(InviteStatus.PENDING);
  }

  @Test
  void deleteByExpiresAtBeforeRemovesOnlyExpiredInvites() {
    OrganizationInvite expired =
        persistInvite(
            persistOrganization(persistUser()),
            "expired@example.com",
            "hash-expired",
            InviteStatus.PENDING);
    expired.setExpiresAt(LocalDateTime.now().minusDays(1));
    organizationInviteRepository.saveAndFlush(expired);

    OrganizationInvite fresh =
        persistInvite(
            persistOrganization(persistUser()),
            "fresh@example.com",
            "hash-fresh",
            InviteStatus.PENDING);

    int deleted = organizationInviteRepository.deleteByExpiresAtBefore(LocalDateTime.now());

    assertThat(deleted).isEqualTo(1);
    assertThat(organizationInviteRepository.findById(expired.getId())).isEmpty();
    assertThat(organizationInviteRepository.findById(fresh.getId())).isPresent();
  }
}
