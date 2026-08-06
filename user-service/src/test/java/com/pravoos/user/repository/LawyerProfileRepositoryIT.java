package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.identity.model.entity.LawyerProfile;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.LawyerProfileRepository;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.config.PiiCryptoConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import(PiiCryptoConfig.class)
@EnableConfigurationProperties(com.pravoos.common.security.PiiCryptoProperties.class)
class LawyerProfileRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private UserRepository userRepository;
  @Autowired private LawyerProfileRepository lawyerProfileRepository;

  private User persistUser(String email) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash("hash");
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.saveAndFlush(user);
  }

  private LawyerProfile persistProfile(User user, String fullName) {
    LawyerProfile profile = new LawyerProfile();
    profile.setUser(user);
    profile.setFullName(fullName);
    return lawyerProfileRepository.saveAndFlush(profile);
  }

  @Test
  void findByUserIdWithUserReturnsProfileWithFetchedUser() {
    User user = persistUser("lawyer@example.com");
    persistProfile(user, "Иван Иванов");

    var result = lawyerProfileRepository.findByUserIdWithUser(user.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getFullName()).isEqualTo("Иван Иванов");
    assertThat(result.get().getUser().getEmail()).isEqualTo("lawyer@example.com");
  }

  @Test
  void findByUserIdWithUserReturnsEmptyForUnknownUserId() {
    assertThat(lawyerProfileRepository.findByUserIdWithUser(UUID.randomUUID())).isEmpty();
  }
}
