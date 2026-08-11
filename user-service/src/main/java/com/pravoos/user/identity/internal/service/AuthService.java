package com.pravoos.user.identity.internal.service;

import com.pravoos.common.web.AiProcessingMode;
import com.pravoos.user.billing.api.PlanClaim;
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
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.exception.AccountLockedException;
import com.pravoos.user.shared.exception.InvalidCredentialsException;
import com.pravoos.user.shared.exception.InvalidRefreshTokenException;
import com.pravoos.user.shared.exception.MfaException;
import com.pravoos.user.shared.service.OutboxEventService;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
  private static final String NEW_LOGIN_TOPIC = "user.new_login";
  private static final DateTimeFormatter LOGIN_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final UserRepository userRepository;
  private final OrgMembershipProvider orgMembershipProvider;
  private final PortalAccessProvider portalAccessProvider;
  private final PlanClaimProvider planClaimProvider;
  private final AiProcessingModeProvider aiProcessingModeProvider;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;
  private final RefreshTokenService refreshTokenService;
  private final LoginAttemptService loginAttemptService;
  private final MfaService mfaService;
  private final MfaChallengeService mfaChallengeService;
  private final ApplicationEventPublisher eventPublisher;
  private final OutboxEventService outboxEventService;
  private final Counter loginSuccessCounter;
  private final Counter loginFailureCounter;
  private final Counter loginLockedCounter;
  private final Counter loginMfaChallengedCounter;

  public AuthService(
      UserRepository userRepository,
      OrgMembershipProvider orgMembershipProvider,
      PortalAccessProvider portalAccessProvider,
      PlanClaimProvider planClaimProvider,
      AiProcessingModeProvider aiProcessingModeProvider,
      JwtTokenProvider jwtTokenProvider,
      PasswordEncoder passwordEncoder,
      RefreshTokenService refreshTokenService,
      LoginAttemptService loginAttemptService,
      MfaService mfaService,
      MfaChallengeService mfaChallengeService,
      ApplicationEventPublisher eventPublisher,
      OutboxEventService outboxEventService,
      MeterRegistry meterRegistry) {
    this.userRepository = userRepository;
    this.orgMembershipProvider = orgMembershipProvider;
    this.portalAccessProvider = portalAccessProvider;
    this.planClaimProvider = planClaimProvider;
    this.aiProcessingModeProvider = aiProcessingModeProvider;
    this.jwtTokenProvider = jwtTokenProvider;
    this.passwordEncoder = passwordEncoder;
    this.refreshTokenService = refreshTokenService;
    this.loginAttemptService = loginAttemptService;
    this.mfaService = mfaService;
    this.mfaChallengeService = mfaChallengeService;
    this.eventPublisher = eventPublisher;
    this.outboxEventService = outboxEventService;
    this.loginSuccessCounter =
        Counter.builder("pravoos.login").tag("result", "success").register(meterRegistry);
    this.loginFailureCounter =
        Counter.builder("pravoos.login").tag("result", "failure").register(meterRegistry);
    this.loginLockedCounter =
        Counter.builder("pravoos.login").tag("result", "locked").register(meterRegistry);
    this.loginMfaChallengedCounter =
        Counter.builder("pravoos.login").tag("result", "mfa_challenged").register(meterRegistry);
  }

  @Transactional
  public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
    String email = EmailNormalizer.normalize(request.email());
    loginAttemptService
        .remainingLockSeconds(email)
        .ifPresent(
            seconds -> {
              loginLockedCounter.increment();
              log.warn("Blocked login attempt for locked account: {}", EmailMasker.mask(email));
              throw new AccountLockedException(seconds);
            });

    User user = userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE).orElse(null);

    if (user == null) {
      passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
      loginAttemptService.recordFailure(email);
      loginFailureCounter.increment();
      log.warn("Failed login attempt for unknown/inactive email: {}", EmailMasker.mask(email));
      throw new InvalidCredentialsException();
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      loginAttemptService.recordFailure(email);
      loginFailureCounter.increment();
      log.warn("Failed login attempt for email: {}", EmailMasker.mask(email));
      throw new InvalidCredentialsException();
    }

    loginAttemptService.reset(email);

    if (mfaService.isMfaEnabled(user.getId())) {
      loginMfaChallengedCounter.increment();
      log.info("Password verified, MFA challenge required: {}", EmailMasker.mask(email));
      return LoginResult.mfaRequired(mfaChallengeService.createChallenge(user.getId()));
    }

    loginSuccessCounter.increment();
    log.info("User authenticated: {}", EmailMasker.mask(email));
    return LoginResult.success(completeLogin(user, ipAddress, userAgent));
  }

  @Transactional
  public TokenResponse completeMfaLogin(
      String mfaToken, String code, String ipAddress, String userAgent) {
    UUID userId = mfaChallengeService.resolve(mfaToken);
    User user =
        userRepository
            .findById(userId)
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(InvalidCredentialsException::new);

    loginAttemptService
        .remainingLockSeconds(user.getEmail())
        .ifPresent(
            seconds -> {
              loginLockedCounter.increment();
              log.warn(
                  "Blocked MFA attempt for locked account: {}", EmailMasker.mask(user.getEmail()));
              throw new AccountLockedException(seconds);
            });

    if (!mfaService.verifyLoginCode(userId, code)) {
      loginAttemptService.recordFailure(user.getEmail());
      loginFailureCounter.increment();
      log.warn("Invalid MFA code during login for user {}", userId);
      mfaChallengeService.registerFailedAttempt(mfaToken);
      throw MfaException.invalidCode();
    }
    mfaChallengeService.invalidate(mfaToken);
    loginAttemptService.reset(user.getEmail());

    loginSuccessCounter.increment();
    log.info("User authenticated via MFA: {}", EmailMasker.mask(user.getEmail()));
    return completeLogin(user, ipAddress, userAgent);
  }

  @Transactional
  public TokenResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
    UUID userId = refreshTokenService.rotate(rawRefreshToken);
    User user =
        userRepository
            .findById(userId)
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(InvalidRefreshTokenException::new);
    return issueTokens(user, ipAddress, userAgent);
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenService.revoke(rawRefreshToken);
  }

  @Transactional
  public TokenResponse issueTokensForUser(UUID userId, String ipAddress, String userAgent) {
    User user =
        userRepository
            .findById(userId)
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(InvalidCredentialsException::new);
    loginSuccessCounter.increment();
    return issueTokens(user, ipAddress, userAgent);
  }

  private TokenResponse completeLogin(User user, String ipAddress, String userAgent) {
    boolean knownDevice = refreshTokenService.isKnownDevice(user.getId(), ipAddress);
    TokenResponse tokens = issueTokens(user, ipAddress, userAgent);
    if (!knownDevice && ipAddress != null) {
      LocalDateTime occurredAt = LocalDateTime.now(ZoneOffset.UTC);
      if (user.isLoginAlertEmail()) {
        eventPublisher.publishEvent(
            new NewLoginEvent(user.getEmail(), ipAddress, userAgent, occurredAt));
      }
      if (user.isLoginAlertTelegram() || user.isLoginAlertPush()) {
        outboxEventService.enqueue(
            NEW_LOGIN_TOPIC,
            user.getId().toString(),
            new NewLoginKafkaPayload(
                user.getId(),
                ipAddress,
                userAgent,
                occurredAt.format(LOGIN_TIME_FORMATTER),
                user.isLoginAlertTelegram(),
                user.isLoginAlertPush()));
      }
      log.info("New-device login detected for {}", EmailMasker.mask(user.getEmail()));
    }
    return tokens;
  }

  private TokenResponse issueTokens(User user, String ipAddress, String userAgent) {
    List<UUID> orgIds = orgMembershipProvider.orgIdsForUser(user.getId());
    List<UUID> clientIds =
        user.getRole() == UserRole.CLIENT
            ? portalAccessProvider.acceptedClientIdsForUser(user.getId())
            : List.of();
    PlanClaim plan =
        user.getRole() == UserRole.CLIENT
            ? null
            : planClaimProvider.effectivePlanFor(user.getId()).orElse(null);
    AiProcessingMode aiProcessingMode = aiProcessingModeProvider.resolveMode(user.getId());
    IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId(), ipAddress, userAgent);
    String accessToken =
        jwtTokenProvider.generateToken(
            user.getId(),
            user.getRole(),
            orgIds,
            clientIds,
            plan,
            aiProcessingMode,
            refreshToken.sessionId());
    return new TokenResponse(
        accessToken, refreshToken.rawToken(), user.getId(), user.getEmail(), user.getRole());
  }
}
