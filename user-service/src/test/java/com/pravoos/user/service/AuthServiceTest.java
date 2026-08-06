package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pravoos.user.billing.api.PlanClaimProvider;
import com.pravoos.user.identity.api.AiProcessingModeProvider;
import com.pravoos.user.identity.api.OrgMembershipProvider;
import com.pravoos.user.identity.api.PortalAccessProvider;
import com.pravoos.user.identity.internal.dto.IssuedRefreshToken;
import com.pravoos.user.identity.internal.dto.LoginRequest;
import com.pravoos.user.identity.internal.dto.LoginResult;
import com.pravoos.user.identity.internal.dto.TokenResponse;
import com.pravoos.user.identity.internal.event.NewLoginEvent;
import com.pravoos.user.identity.internal.event.NewLoginKafkaPayload;
import com.pravoos.user.identity.internal.security.JwtTokenProvider;
import com.pravoos.user.identity.internal.service.*;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.exception.AccountLockedException;
import com.pravoos.user.shared.exception.InvalidCredentialsException;
import com.pravoos.user.shared.exception.MfaException;
import com.pravoos.user.shared.service.OutboxEventService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String EMAIL = "lawyer@example.com";
  private static final String RAW_PASSWORD = "secret123";
  private static final String HASH = "$2a$10$hash";
  private static final String IP = "203.0.113.9";
  private static final String UA = "JUnit-UA";
  private static final UUID SESSION_ID = UUID.randomUUID();

  @Mock private UserRepository userRepository;
  @Mock private OrgMembershipProvider orgMembershipProvider;
  @Mock private PortalAccessProvider portalAccessProvider;
  @Mock private PlanClaimProvider planClaimProvider;
  @Mock private AiProcessingModeProvider aiProcessingModeProvider;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private MfaService mfaService;
  @Mock private MfaChallengeService mfaChallengeService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private OutboxEventService outboxEventService;

  private AuthService authService;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    authService =
        new AuthService(
            userRepository,
            orgMembershipProvider,
            portalAccessProvider,
            planClaimProvider,
            aiProcessingModeProvider,
            jwtTokenProvider,
            passwordEncoder,
            refreshTokenService,
            loginAttemptService,
            mfaService,
            mfaChallengeService,
            eventPublisher,
            outboxEventService,
            meterRegistry);
  }

  @Test
  void login_succeeds_withValidCredentials() {
    User user = activeUser();
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(true);
    when(mfaService.isMfaEnabled(user.getId())).thenReturn(false);
    when(refreshTokenService.isKnownDevice(user.getId(), IP)).thenReturn(true);
    when(jwtTokenProvider.generateToken(
            eq(user.getId()),
            eq(EMAIL),
            eq(UserRole.LAWYER),
            anyList(),
            anyList(),
            any(),
            any(),
            eq(SESSION_ID)))
        .thenReturn("access");
    when(refreshTokenService.issue(user.getId(), IP, UA))
        .thenReturn(new IssuedRefreshToken("refresh", SESSION_ID));

    LoginResult result = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA);

    assertThat(result.mfaRequired()).isFalse();
    TokenResponse response = result.tokens();
    assertThat(response.accessToken()).isEqualTo("access");
    assertThat(response.refreshToken()).isEqualTo("refresh");
    verify(loginAttemptService).reset(EMAIL);
    verify(eventPublisher, never()).publishEvent(any(NewLoginEvent.class));
    assertThat(counter("success")).isEqualTo(1.0);
  }

  @Test
  void login_publishesNewLoginEvent_forUnknownDevice() {
    User user = activeUser();
    user.setLoginAlertPush(false);
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(true);
    when(mfaService.isMfaEnabled(user.getId())).thenReturn(false);
    when(refreshTokenService.isKnownDevice(user.getId(), IP)).thenReturn(false);
    when(refreshTokenService.issue(user.getId(), IP, UA))
        .thenReturn(new IssuedRefreshToken("refresh", SESSION_ID));

    authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA);

    verify(eventPublisher).publishEvent(any(NewLoginEvent.class));
    verify(outboxEventService, never()).enqueue(anyString(), anyString(), any());
  }

  @Test
  void login_enqueuesTelegramOutbox_whenTelegramAlertEnabled_forUnknownDevice() {
    User user = activeUser();
    user.setLoginAlertEmail(false);
    user.setLoginAlertTelegram(true);
    user.setLoginAlertPush(false);
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(true);
    when(mfaService.isMfaEnabled(user.getId())).thenReturn(false);
    when(refreshTokenService.isKnownDevice(user.getId(), IP)).thenReturn(false);
    when(refreshTokenService.issue(user.getId(), IP, UA))
        .thenReturn(new IssuedRefreshToken("refresh", SESSION_ID));

    authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA);

    verify(eventPublisher, never()).publishEvent(any(NewLoginEvent.class));
    verify(outboxEventService)
        .enqueue(
            eq("user.new_login"), eq(user.getId().toString()), any(NewLoginKafkaPayload.class));
  }

  @Test
  void login_enqueuesPushOutbox_whenOnlyPushAlertEnabled_forUnknownDevice() {
    User user = activeUser();
    user.setLoginAlertEmail(false);
    user.setLoginAlertTelegram(false);
    user.setLoginAlertPush(true);
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(true);
    when(mfaService.isMfaEnabled(user.getId())).thenReturn(false);
    when(refreshTokenService.isKnownDevice(user.getId(), IP)).thenReturn(false);
    when(refreshTokenService.issue(user.getId(), IP, UA))
        .thenReturn(new IssuedRefreshToken("refresh", SESSION_ID));

    authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA);

    ArgumentCaptor<NewLoginKafkaPayload> captor =
        ArgumentCaptor.forClass(NewLoginKafkaPayload.class);
    verify(outboxEventService)
        .enqueue(eq("user.new_login"), eq(user.getId().toString()), captor.capture());
    assertThat(captor.getValue().pushEnabled()).isTrue();
    assertThat(captor.getValue().telegramEnabled()).isFalse();
  }

  @Test
  void login_doesNotNotify_forKnownDevice_evenWhenAlertsEnabled() {
    User user = activeUser();
    user.setLoginAlertTelegram(true);
    user.setLoginAlertPush(true);
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(true);
    when(mfaService.isMfaEnabled(user.getId())).thenReturn(false);
    when(refreshTokenService.isKnownDevice(user.getId(), IP)).thenReturn(true);
    when(refreshTokenService.issue(user.getId(), IP, UA))
        .thenReturn(new IssuedRefreshToken("refresh", SESSION_ID));

    authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA);

    verify(eventPublisher, never()).publishEvent(any(NewLoginEvent.class));
    verify(outboxEventService, never()).enqueue(anyString(), anyString(), any());
  }

  @Test
  void login_returnsMfaChallenge_whenMfaEnabled() {
    User user = activeUser();
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(true);
    when(mfaService.isMfaEnabled(user.getId())).thenReturn(true);
    when(mfaChallengeService.createChallenge(user.getId())).thenReturn("challenge-token");

    LoginResult result = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA);

    assertThat(result.mfaRequired()).isTrue();
    assertThat(result.mfaToken()).isEqualTo("challenge-token");
    assertThat(result.tokens()).isNull();
    verify(refreshTokenService, never()).issue(any(), any(), any());
    assertThat(counter("mfa_challenged")).isEqualTo(1.0);
  }

  @Test
  void completeMfaLogin_issuesTokens_forValidCode() {
    User user = activeUser();
    when(mfaChallengeService.resolve("challenge-token")).thenReturn(user.getId());
    when(mfaService.verifyLoginCode(user.getId(), "123456")).thenReturn(true);
    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(refreshTokenService.isKnownDevice(user.getId(), IP)).thenReturn(true);
    when(jwtTokenProvider.generateToken(
            eq(user.getId()),
            eq(EMAIL),
            eq(UserRole.LAWYER),
            anyList(),
            anyList(),
            any(),
            any(),
            eq(SESSION_ID)))
        .thenReturn("access");
    when(refreshTokenService.issue(user.getId(), IP, UA))
        .thenReturn(new IssuedRefreshToken("refresh", SESSION_ID));

    TokenResponse response = authService.completeMfaLogin("challenge-token", "123456", IP, UA);

    assertThat(response.accessToken()).isEqualTo("access");
    verify(mfaChallengeService).invalidate("challenge-token");
    assertThat(counter("success")).isEqualTo(1.0);
  }

  @Test
  void completeMfaLogin_rejectsInvalidCode_andRecordsAttempt() {
    UUID userId = UUID.randomUUID();
    when(mfaChallengeService.resolve("challenge-token")).thenReturn(userId);
    when(mfaService.verifyLoginCode(userId, "000000")).thenReturn(false);

    assertThatThrownBy(() -> authService.completeMfaLogin("challenge-token", "000000", IP, UA))
        .isInstanceOf(MfaException.class);

    verify(mfaChallengeService).registerFailedAttempt("challenge-token");
    verify(refreshTokenService, never()).issue(any(), any(), any());
  }

  @Test
  void login_fails_andRecordsFailure_whenPasswordWrong() {
    User user = activeUser();
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(false);

    assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(loginAttemptService).recordFailure(EMAIL);
    verify(refreshTokenService, never()).issue(any(), any(), any());
    assertThat(counter("failure")).isEqualTo(1.0);
  }

  @Test
  void login_fails_forUnknownUser_withoutLeakingExistence() {
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
    when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwordEncoder).matches(eq(RAW_PASSWORD), anyString());
    verify(loginAttemptService).recordFailure(EMAIL);
    assertThat(counter("failure")).isEqualTo(1.0);
  }

  @Test
  void login_isBlocked_whenAccountLocked() {
    when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.of(120L));

    assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD), IP, UA))
        .isInstanceOf(AccountLockedException.class);

    verify(userRepository, never()).findByEmailAndStatus(anyString(), any());
    assertThat(counter("locked")).isEqualTo(1.0);
  }

  private double counter(String result) {
    return meterRegistry.get("pravoos.login").tag("result", result).counter().count();
  }

  private User activeUser() {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(EMAIL);
    user.setPasswordHash(HASH);
    user.setRole(UserRole.LAWYER);
    user.setStatus(UserStatus.ACTIVE);
    return user;
  }
}
