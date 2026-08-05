package com.pravoos.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.registration.internal.model.entity.LawyerApplication;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.repository.LawyerApplicationRepository;
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
class LawyerApplicationRepositoryIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @Autowired private LawyerApplicationRepository lawyerApplicationRepository;

  private LawyerApplication persistApplication(String email, ApplicationStatus status) {
    LawyerApplication application = new LawyerApplication();
    application.setEmail(email);
    application.setFullName("Иван Иванов");
    application.setPasswordHash("hash");
    application.setStatus(status);
    application.setStatusToken(UUID.randomUUID().toString());
    return lawyerApplicationRepository.saveAndFlush(application);
  }

  private void setSubmittedAt(LawyerApplication application, LocalDateTime submittedAt) {
    try {
      java.lang.reflect.Field field = LawyerApplication.class.getDeclaredField("submittedAt");
      field.setAccessible(true);
      field.set(application, submittedAt);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void findByIdForUpdateReturnsExistingApplication() {
    LawyerApplication application =
        persistApplication("locked@example.com", ApplicationStatus.PENDING);

    assertThat(lawyerApplicationRepository.findByIdForUpdate(application.getId())).isPresent();
    assertThat(lawyerApplicationRepository.findByIdForUpdate(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findByStatusOrderBySubmittedAtDescReturnsOnlyMatchingStatusNewestFirst() {
    LawyerApplication older = persistApplication("older@example.com", ApplicationStatus.PENDING);
    setSubmittedAt(older, LocalDateTime.now().minusDays(2));
    lawyerApplicationRepository.saveAndFlush(older);

    LawyerApplication newer = persistApplication("newer@example.com", ApplicationStatus.PENDING);
    setSubmittedAt(newer, LocalDateTime.now().minusDays(1));
    lawyerApplicationRepository.saveAndFlush(newer);

    persistApplication("approved@example.com", ApplicationStatus.APPROVED);

    List<LawyerApplication> result =
        lawyerApplicationRepository.findByStatusOrderBySubmittedAtDesc(
            ApplicationStatus.PENDING, PageRequest.of(0, 10));

    assertThat(result)
        .extracting(LawyerApplication::getEmail)
        .containsExactly("newer@example.com", "older@example.com");
  }

  @Test
  void findAllByOrderBySubmittedAtDescReturnsAllApplicationsNewestFirst() {
    LawyerApplication first = persistApplication("first@example.com", ApplicationStatus.PENDING);
    setSubmittedAt(first, LocalDateTime.now().minusDays(2));
    lawyerApplicationRepository.saveAndFlush(first);

    LawyerApplication second = persistApplication("second@example.com", ApplicationStatus.APPROVED);
    setSubmittedAt(second, LocalDateTime.now().minusDays(1));
    lawyerApplicationRepository.saveAndFlush(second);

    List<LawyerApplication> result =
        lawyerApplicationRepository.findAllByOrderBySubmittedAtDesc(PageRequest.of(0, 10));

    assertThat(result)
        .extracting(LawyerApplication::getEmail)
        .containsExactly("second@example.com", "first@example.com");
  }

  @Test
  void countByStatusCountsOnlyMatchingApplications() {
    persistApplication("pending1@example.com", ApplicationStatus.PENDING);
    persistApplication("pending2@example.com", ApplicationStatus.PENDING);
    persistApplication("approved@example.com", ApplicationStatus.APPROVED);

    assertThat(lawyerApplicationRepository.countByStatus(ApplicationStatus.PENDING)).isEqualTo(2);
  }

  @Test
  void existsByEmailAndStatusMatchesOnlyGivenStatus() {
    persistApplication("exists@example.com", ApplicationStatus.PENDING);

    assertThat(
            lawyerApplicationRepository.existsByEmailAndStatus(
                "exists@example.com", ApplicationStatus.PENDING))
        .isTrue();
    assertThat(
            lawyerApplicationRepository.existsByEmailAndStatus(
                "exists@example.com", ApplicationStatus.APPROVED))
        .isFalse();
  }

  @Test
  void findByEmailVerificationTokenReturnsMatchingApplication() {
    LawyerApplication application =
        persistApplication("verify@example.com", ApplicationStatus.PENDING);
    application.setEmailVerificationToken("verify-token");
    lawyerApplicationRepository.saveAndFlush(application);

    assertThat(lawyerApplicationRepository.findByEmailVerificationToken("verify-token"))
        .isPresent();
    assertThat(lawyerApplicationRepository.findByEmailVerificationToken("missing-token")).isEmpty();
  }

  @Test
  void findByStatusTokenReturnsMatchingApplication() {
    LawyerApplication application =
        persistApplication("status-token@example.com", ApplicationStatus.PENDING);

    assertThat(lawyerApplicationRepository.findByStatusToken(application.getStatusToken()))
        .isPresent();
    assertThat(lawyerApplicationRepository.findByStatusToken("missing-token")).isEmpty();
  }

  @Test
  void findByEmailAndStatusAndEmailVerifiedFalseExcludesVerifiedApplications() {
    persistApplication("unverified@example.com", ApplicationStatus.PENDING);
    LawyerApplication verified =
        persistApplication("verified@example.com", ApplicationStatus.PENDING);
    verified.setEmailVerified(true);
    lawyerApplicationRepository.saveAndFlush(verified);

    assertThat(
            lawyerApplicationRepository.findByEmailAndStatusAndEmailVerifiedFalse(
                "unverified@example.com", ApplicationStatus.PENDING))
        .isPresent();
    assertThat(
            lawyerApplicationRepository.findByEmailAndStatusAndEmailVerifiedFalse(
                "verified@example.com", ApplicationStatus.PENDING))
        .isEmpty();
  }

  @Test
  void clearExpiredVerificationTokensOnlyClearsExpiredUnverifiedTokens() {
    LawyerApplication expiredUnverified =
        persistApplication("expired@example.com", ApplicationStatus.PENDING);
    expiredUnverified.setEmailVerificationToken("expired-token");
    expiredUnverified.setEmailVerificationExpiresAt(LocalDateTime.now().minusHours(1));
    lawyerApplicationRepository.saveAndFlush(expiredUnverified);

    LawyerApplication activeUnverified =
        persistApplication("active@example.com", ApplicationStatus.PENDING);
    activeUnverified.setEmailVerificationToken("active-token");
    activeUnverified.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(1));
    lawyerApplicationRepository.saveAndFlush(activeUnverified);

    LawyerApplication expiredVerified =
        persistApplication("expired-verified@example.com", ApplicationStatus.PENDING);
    expiredVerified.setEmailVerificationToken("expired-verified-token");
    expiredVerified.setEmailVerificationExpiresAt(LocalDateTime.now().minusHours(1));
    expiredVerified.setEmailVerified(true);
    lawyerApplicationRepository.saveAndFlush(expiredVerified);

    int updated = lawyerApplicationRepository.clearExpiredVerificationTokens(LocalDateTime.now());

    assertThat(updated).isEqualTo(1);
    assertThat(
            lawyerApplicationRepository
                .findById(expiredUnverified.getId())
                .orElseThrow()
                .getEmailVerificationToken())
        .isNull();
    assertThat(
            lawyerApplicationRepository
                .findById(activeUnverified.getId())
                .orElseThrow()
                .getEmailVerificationToken())
        .isEqualTo("active-token");
    assertThat(
            lawyerApplicationRepository
                .findById(expiredVerified.getId())
                .orElseThrow()
                .getEmailVerificationToken())
        .isEqualTo("expired-verified-token");
  }

  @Test
  void deleteByEmailRemovesAllApplicationsForThatEmail() {
    persistApplication("delete-me@example.com", ApplicationStatus.PENDING);
    persistApplication("keep-me@example.com", ApplicationStatus.PENDING);

    int deleted = lawyerApplicationRepository.deleteByEmail("delete-me@example.com");

    assertThat(deleted).isEqualTo(1);
    assertThat(
            lawyerApplicationRepository.existsByEmailAndStatus(
                "delete-me@example.com", ApplicationStatus.PENDING))
        .isFalse();
    assertThat(
            lawyerApplicationRepository.existsByEmailAndStatus(
                "keep-me@example.com", ApplicationStatus.PENDING))
        .isTrue();
  }
}
