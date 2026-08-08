package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.pravoos.user.billing.api.SubscriptionProvisioner;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.privacy.api.ConsentRecorder;
import com.pravoos.user.registration.internal.dto.ApplicationSubmissionResponse;
import com.pravoos.user.registration.internal.dto.ApplyRequest;
import com.pravoos.user.registration.internal.event.ApplicationSubmittedSpringEvent;
import com.pravoos.user.registration.internal.model.entity.LawyerApplication;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.repository.LawyerApplicationRepository;
import com.pravoos.user.registration.internal.service.ApplicationService;
import com.pravoos.user.registration.internal.service.EmailVerificationService;
import com.pravoos.user.shared.exception.ApplicationAlreadyExistsException;
import com.pravoos.user.shared.exception.ConsentRequiredException;
import com.pravoos.user.shared.exception.EmailAlreadyExistsException;
import com.pravoos.user.shared.security.TokenHasher;
import com.pravoos.user.shared.service.OutboxEventService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

  @Mock private LawyerApplicationRepository applicationRepository;
  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private EmailVerificationService emailVerificationService;
  @Mock private OutboxEventService outboxEventService;
  @Mock private TokenHasher tokenHasher;
  @Mock private SubscriptionProvisioner subscriptionProvisioner;
  @Mock private ConsentRecorder consentRecorder;

  private ApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new ApplicationService(
            applicationRepository,
            userRepository,
            passwordEncoder,
            eventPublisher,
            emailVerificationService,
            outboxEventService,
            tokenHasher,
            subscriptionProvisioner,
            consentRecorder,
            new SimpleMeterRegistry());
  }

  @Test
  void submitApplication_throwsConsentRequired_whenPersonalDataConsentMissing() {
    ApplyRequest request = applyRequest(false, true);

    assertThatThrownBy(() -> service.submitApplication(request, "1.2.3.4", "UA"))
        .isInstanceOf(ConsentRequiredException.class);
    verifyNoInteractions(applicationRepository, userRepository, eventPublisher, outboxEventService);
  }

  @Test
  void submitApplication_throwsConsentRequired_whenCrossBorderConsentMissing() {
    ApplyRequest request = applyRequest(true, false);

    assertThatThrownBy(() -> service.submitApplication(request, "1.2.3.4", "UA"))
        .isInstanceOf(ConsentRequiredException.class);
    verifyNoInteractions(applicationRepository, userRepository, eventPublisher, outboxEventService);
  }

  @Test
  void submitApplication_throwsApplicationAlreadyExists_whenPendingApplicationForEmailExists() {
    ApplyRequest request = applyRequest(true, true);
    when(applicationRepository.existsByEmailAndStatus(
            request.email().toLowerCase(), ApplicationStatus.PENDING))
        .thenReturn(true);

    assertThatThrownBy(() -> service.submitApplication(request, "1.2.3.4", "UA"))
        .isInstanceOf(ApplicationAlreadyExistsException.class);
    verify(applicationRepository, never()).save(any());
    verifyNoInteractions(userRepository, eventPublisher, outboxEventService);
  }

  @Test
  void submitApplication_throwsEmailAlreadyExists_whenUserWithEmailAlreadyRegistered() {
    ApplyRequest request = applyRequest(true, true);
    when(applicationRepository.existsByEmailAndStatus(
            request.email().toLowerCase(), ApplicationStatus.PENDING))
        .thenReturn(false);
    when(userRepository.existsByEmail(request.email().toLowerCase())).thenReturn(true);

    assertThatThrownBy(() -> service.submitApplication(request, "1.2.3.4", "UA"))
        .isInstanceOf(EmailAlreadyExistsException.class);
    verify(applicationRepository, never()).save(any());
    verifyNoInteractions(eventPublisher, outboxEventService);
  }

  @Test
  void submitApplication_savesApplicationAndPublishesEvents_whenConsentsGiven() {
    ApplyRequest request = applyRequest(true, true);
    when(applicationRepository.existsByEmailAndStatus(
            request.email().toLowerCase(), ApplicationStatus.PENDING))
        .thenReturn(false);
    when(userRepository.existsByEmail(request.email().toLowerCase())).thenReturn(false);
    when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
    when(emailVerificationService.generateToken())
        .thenReturn("raw-verification-token", "raw-status-token");
    when(emailVerificationService.tokenExpiry())
        .thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
    when(tokenHasher.sha256Hex("raw-verification-token")).thenReturn("hashed-verification-token");
    when(applicationRepository.save(any(LawyerApplication.class)))
        .thenAnswer(
            call -> {
              LawyerApplication application = call.getArgument(0);
              ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
              return application;
            });

    ApplicationSubmissionResponse response = service.submitApplication(request, "1.2.3.4", "UA");

    ArgumentCaptor<LawyerApplication> captor = ArgumentCaptor.forClass(LawyerApplication.class);
    verify(applicationRepository).save(captor.capture());
    LawyerApplication saved = captor.getValue();
    assertThat(saved.getEmail()).isEqualTo(request.email().toLowerCase());
    assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
    assertThat(saved.getConsentIp()).isEqualTo("1.2.3.4");
    assertThat(saved.getConsentUserAgent()).isEqualTo("UA");
    assertThat(saved.isConsentCrossBorder()).isTrue();
    assertThat(saved.getEmailVerificationToken()).isEqualTo("hashed-verification-token");
    assertThat(saved.getStatusToken()).isEqualTo("raw-status-token");

    assertThat(response.statusToken()).isEqualTo("raw-status-token");
    assertThat(response.application().email()).isEqualTo(request.email().toLowerCase());

    verify(eventPublisher)
        .publishEvent(
            argThat(
                (ApplicationSubmittedSpringEvent event) ->
                    event.rawVerificationToken().equals("raw-verification-token")));
    verify(outboxEventService)
        .enqueue(eq("application.submitted"), eq(saved.getId().toString()), any());
  }

  @Test
  void submitApplication_storesEmailVerificationTokenHashed_butStatusTokenRaw() {
    ApplyRequest request = applyRequest(true, true);
    when(userRepository.existsByEmail(any())).thenReturn(false);
    when(emailVerificationService.generateToken())
        .thenReturn("token-for-verification", "token-for-status");
    when(emailVerificationService.tokenExpiry())
        .thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusHours(24));
    when(tokenHasher.sha256Hex("token-for-verification")).thenReturn("hashed-token");
    when(applicationRepository.save(any(LawyerApplication.class)))
        .thenAnswer(
            call -> {
              LawyerApplication application = call.getArgument(0);
              ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
              return application;
            });

    service.submitApplication(request, "1.2.3.4", "UA");

    ArgumentCaptor<LawyerApplication> captor = ArgumentCaptor.forClass(LawyerApplication.class);
    verify(applicationRepository).save(captor.capture());
    assertThat(captor.getValue().getEmailVerificationToken()).isEqualTo("hashed-token");
    assertThat(captor.getValue().getStatusToken()).isEqualTo("token-for-status");
  }

  private ApplyRequest applyRequest(boolean personalDataConsent, boolean crossBorderConsent) {
    return new ApplyRequest(
        "Applicant@Example.com",
        "Иван Иванов",
        "Password123",
        "Гражданское право",
        "+7 999 123-45-67",
        personalDataConsent,
        crossBorderConsent,
        false,
        "2.0");
  }
}
