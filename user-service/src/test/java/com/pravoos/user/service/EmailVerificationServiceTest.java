package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.registration.internal.event.VerificationEmailRequestedEvent;
import com.pravoos.user.registration.internal.model.entity.LawyerApplication;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.repository.LawyerApplicationRepository;
import com.pravoos.user.registration.internal.service.EmailVerificationService;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.exception.InvalidVerificationTokenException;
import com.pravoos.user.shared.security.TokenHasher;
import com.pravoos.user.shared.service.EmailRateLimiter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  @Mock private LawyerApplicationRepository applicationRepository;
  @Mock private EmailRateLimiter emailRateLimiter;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private TokenHasher tokenHasher;

  private ResendProperties resendProperties;
  private EmailVerificationService service;

  @BeforeEach
  void setUp() {
    resendProperties = new ResendProperties("key", "from@pravoos.com", "https://app", 24, 2, 5);
    service =
        new EmailVerificationService(
            applicationRepository, resendProperties, emailRateLimiter, eventPublisher, tokenHasher);
  }

  @Test
  void generateToken_returnsHex64Chars() {
    String token = service.generateToken();

    assertThat(token).hasSize(64).matches("[0-9a-f]+");
  }

  @Test
  void generateToken_generatesDifferentValues() {
    assertThat(service.generateToken()).isNotEqualTo(service.generateToken());
  }

  @Test
  void tokenExpiry_isConfiguredHoursAhead() {
    LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).plusHours(24);

    LocalDateTime expiry = service.tokenExpiry();

    assertThat(expiry).isAfterOrEqualTo(before);
    assertThat(expiry).isBefore(before.plusMinutes(1));
  }

  @Test
  void verifyToken_throws_whenTokenNotFound() {
    when(tokenHasher.sha256Hex("raw-token")).thenReturn("hashed-token");
    when(applicationRepository.findByEmailVerificationToken("hashed-token"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verifyToken("raw-token"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verifyToken_throws_whenExpired() {
    LawyerApplication application = new LawyerApplication();
    application.setEmailVerificationExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    when(tokenHasher.sha256Hex("raw-token")).thenReturn("hashed-token");
    when(applicationRepository.findByEmailVerificationToken("hashed-token"))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.verifyToken("raw-token"))
        .isInstanceOf(InvalidVerificationTokenException.class);
    verify(applicationRepository, never()).save(any());
  }

  @Test
  void verifyToken_throws_whenExpiryMissing() {
    LawyerApplication application = new LawyerApplication();
    application.setEmailVerificationExpiresAt(null);
    when(tokenHasher.sha256Hex("raw-token")).thenReturn("hashed-token");
    when(applicationRepository.findByEmailVerificationToken("hashed-token"))
        .thenReturn(Optional.of(application));

    assertThatThrownBy(() -> service.verifyToken("raw-token"))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }

  @Test
  void verifyToken_marksVerified_andClearsToken_whenValid() {
    LawyerApplication application = new LawyerApplication();
    application.setEmail("lawyer@example.com");
    application.setEmailVerificationExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
    when(tokenHasher.sha256Hex("raw-token")).thenReturn("hashed-token");
    when(applicationRepository.findByEmailVerificationToken("hashed-token"))
        .thenReturn(Optional.of(application));

    service.verifyToken("raw-token");

    assertThat(application.isEmailVerified()).isTrue();
    assertThat(application.getEmailVerificationToken()).isNull();
    assertThat(application.getEmailVerificationExpiresAt()).isNull();
    verify(applicationRepository).save(application);
  }

  @Test
  void resendVerification_doesNothing_whenRateLimited() {
    when(emailRateLimiter.allow("verification", "lawyer@example.com")).thenReturn(false);

    service.resendVerification("Lawyer@Example.com");

    verify(applicationRepository, never())
        .findByEmailAndStatusAndEmailVerifiedFalse(anyString(), any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void resendVerification_doesNothing_whenNoPendingUnverifiedApplication() {
    when(emailRateLimiter.allow("verification", "lawyer@example.com")).thenReturn(true);
    when(applicationRepository.findByEmailAndStatusAndEmailVerifiedFalse(
            "lawyer@example.com", ApplicationStatus.PENDING))
        .thenReturn(Optional.empty());

    service.resendVerification("lawyer@example.com");

    verify(applicationRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void resendVerification_issuesNewTokenAndPublishesEvent_whenApplicationPending() {
    LawyerApplication application = new LawyerApplication();
    application.setEmail("lawyer@example.com");
    when(emailRateLimiter.allow("verification", "lawyer@example.com")).thenReturn(true);
    when(applicationRepository.findByEmailAndStatusAndEmailVerifiedFalse(
            "lawyer@example.com", ApplicationStatus.PENDING))
        .thenReturn(Optional.of(application));
    when(tokenHasher.sha256Hex(anyString())).thenReturn("hashed-new-token");

    service.resendVerification("lawyer@example.com");

    assertThat(application.getEmailVerificationToken()).isEqualTo("hashed-new-token");
    assertThat(application.getEmailVerificationExpiresAt()).isNotNull();
    verify(applicationRepository).save(application);

    ArgumentCaptor<VerificationEmailRequestedEvent> eventCaptor =
        ArgumentCaptor.forClass(VerificationEmailRequestedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().email()).isEqualTo("lawyer@example.com");
    assertThat(eventCaptor.getValue().rawToken()).isNotBlank();
  }

  @Test
  void purgeExpiredVerificationTokens_delegatesToRepository() {
    when(applicationRepository.clearExpiredVerificationTokens(any())).thenReturn(4);

    service.purgeExpiredVerificationTokens();

    verify(applicationRepository).clearExpiredVerificationTokens(any());
  }

  @Test
  void purgeExpiredVerificationTokens_doesNotThrow_whenNothingCleared() {
    when(applicationRepository.clearExpiredVerificationTokens(any())).thenReturn(0);

    service.purgeExpiredVerificationTokens();
  }
}
