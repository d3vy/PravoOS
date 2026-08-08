package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.collaboration.internal.model.entity.Organization;
import com.pravoos.user.collaboration.internal.model.entity.OrganizationMembership;
import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import com.pravoos.user.collaboration.internal.repository.OrganizationMembershipRepository;
import com.pravoos.user.collaboration.internal.repository.OrganizationRepository;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
class OrganizationMembershipRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private OrganizationMembershipRepository membershipRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private UserRepository userRepository;

  private final AtomicInteger seq = new AtomicInteger();

  @Test
  void countMembersByOrgIdsReturnsOneRowPerOrganization() {
    UUID ownerId = persistUser();
    UUID firstOrg = persistOrganization(ownerId);
    UUID secondOrg = persistOrganization(ownerId);
    UUID emptyOrg = persistOrganization(ownerId);

    persistMembership(firstOrg, ownerId, OrgRole.OWNER);
    persistMembership(firstOrg, persistUser(), OrgRole.MEMBER);
    persistMembership(firstOrg, persistUser(), OrgRole.MANAGER);
    persistMembership(secondOrg, ownerId, OrgRole.OWNER);

    Map<UUID, Long> counts =
        membershipRepository.countMembersByOrgIds(List.of(firstOrg, secondOrg, emptyOrg)).stream()
            .collect(
                Collectors.toMap(
                    OrganizationMembershipRepository.OrgMemberCount::getOrgId,
                    OrganizationMembershipRepository.OrgMemberCount::getMemberCount));

    assertThat(counts).containsEntry(firstOrg, 3L).containsEntry(secondOrg, 1L);
    assertThat(counts).doesNotContainKey(emptyOrg);
  }

  @Test
  void countMembersByOrgIdsMatchesPerOrganizationCount() {
    UUID ownerId = persistUser();
    UUID orgId = persistOrganization(ownerId);
    persistMembership(orgId, ownerId, OrgRole.OWNER);
    persistMembership(orgId, persistUser(), OrgRole.MEMBER);

    Map<UUID, Long> counts =
        membershipRepository.countMembersByOrgIds(List.of(orgId)).stream()
            .collect(
                Collectors.toMap(
                    OrganizationMembershipRepository.OrgMemberCount::getOrgId,
                    OrganizationMembershipRepository.OrgMemberCount::getMemberCount));

    assertThat(counts.get(orgId)).isEqualTo(membershipRepository.countByOrgId(orgId));
  }

  private UUID persistUser() {
    User user = new User();
    user.setEmail("membership-user-" + seq.incrementAndGet() + "@example.com");
    user.setPasswordHash("hash");
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.saveAndFlush(user).getId();
  }

  private UUID persistOrganization(UUID ownerId) {
    Organization organization = new Organization();
    organization.setName("Org " + seq.incrementAndGet());
    organization.setOwnerId(ownerId);
    return organizationRepository.saveAndFlush(organization).getId();
  }

  private void persistMembership(UUID orgId, UUID userId, OrgRole role) {
    OrganizationMembership membership = new OrganizationMembership();
    membership.setOrgId(orgId);
    membership.setUserId(userId);
    membership.setOrgRole(role);
    membershipRepository.saveAndFlush(membership);
  }
}
