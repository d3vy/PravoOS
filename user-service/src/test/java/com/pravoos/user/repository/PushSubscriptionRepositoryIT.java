package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.push.internal.model.entity.PushSubscription;
import com.pravoos.user.push.internal.repository.PushSubscriptionRepository;
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
class PushSubscriptionRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private PushSubscriptionRepository pushSubscriptionRepository;
  @Autowired private UserRepository userRepository;

  private final AtomicInteger emailSeq = new AtomicInteger();

  private UUID persistUser() {
    User user = new User();
    user.setEmail("push-user-" + emailSeq.incrementAndGet() + "@example.com");
    user.setPasswordHash("hash");
    user.setRole(UserRole.CLIENT);
    user.setStatus(UserStatus.ACTIVE);
    return userRepository.saveAndFlush(user).getId();
  }

  private PushSubscription persistSubscription(UUID userId, String endpoint) {
    PushSubscription subscription = new PushSubscription();
    subscription.setUserId(userId);
    subscription.setEndpoint(endpoint);
    subscription.setP256dhKey("p256dh");
    subscription.setAuthKey("auth");
    return pushSubscriptionRepository.saveAndFlush(subscription);
  }

  @Test
  void findByEndpointReturnsMatchingSubscription() {
    persistSubscription(persistUser(), "https://push.example.com/a");

    assertThat(pushSubscriptionRepository.findByEndpoint("https://push.example.com/a")).isPresent();
    assertThat(pushSubscriptionRepository.findByEndpoint("https://push.example.com/missing"))
        .isEmpty();
  }

  @Test
  void findByUserIdOrderByCreatedAtDescOrdersNewestFirst() {
    UUID userId = persistUser();
    PushSubscription older = persistSubscription(userId, "https://push.example.com/older");
    older.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
    pushSubscriptionRepository.saveAndFlush(older);

    PushSubscription newer = persistSubscription(userId, "https://push.example.com/newer");
    newer.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    pushSubscriptionRepository.saveAndFlush(newer);

    persistSubscription(persistUser(), "https://push.example.com/other-user");

    assertThat(pushSubscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId))
        .extracting(PushSubscription::getEndpoint)
        .containsExactly("https://push.example.com/newer", "https://push.example.com/older");
  }

  @Test
  void countByUserIdCountsOnlyMatchingUser() {
    UUID userId = persistUser();
    persistSubscription(userId, "https://push.example.com/1");
    persistSubscription(userId, "https://push.example.com/2");
    persistSubscription(persistUser(), "https://push.example.com/3");

    assertThat(pushSubscriptionRepository.countByUserId(userId)).isEqualTo(2);
  }

  @Test
  void deleteByEndpointRemovesMatchingRowAndReturnsCount() {
    persistSubscription(persistUser(), "https://push.example.com/to-delete");

    int deleted = pushSubscriptionRepository.deleteByEndpoint("https://push.example.com/to-delete");

    assertThat(deleted).isEqualTo(1);
    assertThat(pushSubscriptionRepository.findByEndpoint("https://push.example.com/to-delete"))
        .isEmpty();
    assertThat(pushSubscriptionRepository.deleteByEndpoint("https://push.example.com/missing"))
        .isZero();
  }

  @Test
  void deleteByEndpointAndUserIdOnlyRemovesWhenBothMatch() {
    UUID owner = persistUser();
    persistSubscription(owner, "https://push.example.com/owned");

    int mismatchedUser =
        pushSubscriptionRepository.deleteByEndpointAndUserId(
            "https://push.example.com/owned", UUID.randomUUID());
    assertThat(mismatchedUser).isZero();
    assertThat(pushSubscriptionRepository.findByEndpoint("https://push.example.com/owned"))
        .isPresent();

    int matched =
        pushSubscriptionRepository.deleteByEndpointAndUserId(
            "https://push.example.com/owned", owner);
    assertThat(matched).isEqualTo(1);
    assertThat(pushSubscriptionRepository.findByEndpoint("https://push.example.com/owned"))
        .isEmpty();
  }
}
