package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.LawyerProfileRepository;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.config.PiiCryptoConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(PiiCryptoConfig.class)
@EnableConfigurationProperties(com.pravoos.common.security.PiiCryptoProperties.class)
class UserRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private UserRepository userRepository;
  @Autowired private LawyerProfileRepository lawyerProfileRepository;

  private User persistUser(String email, UserRole role, UserStatus status, boolean digestPush) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hash");
    user.setRole(role);
    user.setStatus(status);
    user.setDigestPush(digestPush);
    return userRepository.saveAndFlush(user);
  }

  private void attachProfile(User user, String fullName) {
    LawyerProfile profile = new LawyerProfile();
    profile.setUser(user);
    profile.setFullName(fullName);
    user.setLawyerProfile(profile);
    lawyerProfileRepository.saveAndFlush(profile);
  }

  @Test
  void findByEmailReturnsMatchingUser() {
    persistUser("lawyer@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);

    assertThat(userRepository.findByEmail("lawyer@example.com")).isPresent();
    assertThat(userRepository.findByEmail("missing@example.com")).isEmpty();
  }

  @Test
  void findByEmailAndStatusMatchesOnlyGivenStatus() {
    persistUser("active@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);

    assertThat(userRepository.findByEmailAndStatus("active@example.com", UserStatus.ACTIVE))
        .isPresent();
    assertThat(userRepository.findByEmailAndStatus("active@example.com", UserStatus.REJECTED))
        .isEmpty();
  }

  @Test
  void existsByEmailReflectsPersistedState() {
    persistUser("exists@example.com", UserRole.CLIENT, UserStatus.ACTIVE, true);

    assertThat(userRepository.existsByEmail("exists@example.com")).isTrue();
    assertThat(userRepository.existsByEmail("absent@example.com")).isFalse();
  }

  @Test
  void existsByRoleReflectsPersistedState() {
    assertThat(userRepository.existsByRole(UserRole.ADMIN)).isFalse();

    persistUser("admin@example.com", UserRole.ADMIN, UserStatus.ACTIVE, true);

    assertThat(userRepository.existsByRole(UserRole.ADMIN)).isTrue();
  }

  @Test
  void countByRoleAndStatusCountsOnlyMatchingUsers() {
    persistUser("lawyer1@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    persistUser("lawyer2@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    persistUser("lawyer3@example.com", UserRole.LAWYER, UserStatus.REJECTED, true);
    persistUser("client1@example.com", UserRole.CLIENT, UserStatus.ACTIVE, true);

    assertThat(userRepository.countByRoleAndStatus(UserRole.LAWYER, UserStatus.ACTIVE))
        .isEqualTo(2);
  }

  @Test
  void countByRoleAndStatusAndCreatedAtAfterExcludesOlderUsers() {
    User olderUser = persistUser("older@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    olderUser.setCreatedAt(LocalDateTime.now().minusDays(10));
    userRepository.saveAndFlush(olderUser);

    persistUser("newer@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);

    long recentCount =
        userRepository.countByRoleAndStatusAndCreatedAtAfter(
            UserRole.LAWYER, UserStatus.ACTIVE, LocalDateTime.now().minusDays(1));

    assertThat(recentCount).isEqualTo(1);
  }

  @Test
  void findByRoleAndStatusWithProfileFetchesLawyerProfile() {
    User user = persistUser("profiled@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    attachProfile(user, "Иван Иванов");
    persistUser("noprofile@example.com", UserRole.CLIENT, UserStatus.ACTIVE, true);

    List<User> result =
        userRepository.findByRoleAndStatusWithProfile(UserRole.LAWYER, UserStatus.ACTIVE);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getLawyerProfile()).isNotNull();
    assertThat(result.get(0).getLawyerProfile().getFullName()).isEqualTo("Иван Иванов");
  }

  @Test
  void findByRoleAndStatusWithProfilePageableOrdersByCreatedAtDesc() {
    User first = persistUser("first@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    first.setCreatedAt(LocalDateTime.now().minusDays(2));
    userRepository.saveAndFlush(first);

    User second = persistUser("second@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    second.setCreatedAt(LocalDateTime.now().minusDays(1));
    userRepository.saveAndFlush(second);

    List<User> result =
        userRepository.findByRoleAndStatusWithProfile(
            UserRole.LAWYER, UserStatus.ACTIVE, PageRequest.of(0, 10));

    assertThat(result)
        .extracting(User::getEmail)
        .containsExactly("second@example.com", "first@example.com");
  }

  @Test
  void findByIdInWithProfileReturnsOnlyRequestedIds() {
    User first = persistUser("target1@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    User second = persistUser("target2@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);
    persistUser("other@example.com", UserRole.LAWYER, UserStatus.ACTIVE, true);

    List<User> result =
        userRepository.findByIdInWithProfile(List.of(first.getId(), second.getId()));

    assertThat(result)
        .extracting(User::getId)
        .containsExactlyInAnyOrder(first.getId(), second.getId());
  }

  @Test
  void findByIdInWithProfileReturnsEmptyForUnknownIds() {
    assertThat(userRepository.findByIdInWithProfile(List.of(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void findIdsByIdInAndDigestPushTrueFiltersByDigestPushFlag() {
    User digestEnabled =
        persistUser("digest-on@example.com", UserRole.CLIENT, UserStatus.ACTIVE, true);
    User digestDisabled =
        persistUser("digest-off@example.com", UserRole.CLIENT, UserStatus.ACTIVE, false);

    List<UUID> result =
        userRepository.findIdsByIdInAndDigestPushTrue(
            List.of(digestEnabled.getId(), digestDisabled.getId()));

    assertThat(result).containsExactly(digestEnabled.getId());
  }
}
