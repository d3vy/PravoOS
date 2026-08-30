package com.pravoos.user.identity.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.identity.internal.model.entity.PasswordResetToken;
import com.pravoos.user.identity.internal.repository.PasswordResetTokenRepository;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.exception.InvalidPasswordResetTokenException;
import com.pravoos.user.shared.security.TokenHasher;
import com.pravoos.user.shared.service.EmailRateLimiter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  private static final String RAW_TOKEN = "raw-reset-token";
  private static final String NEW_PASSWORD = "new-secret-1";
  private static final String NEW_HASH = "$2a$10$newhash";

  @Mock private UserRepository userRepository;
  @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private RefreshTokenFamilyRevoker refreshTokenFamilyRevoker;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private EmailRateLimiter emailRateLimiter;
  @Mock private TokenDenylistService tokenDenylistService;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final TokenHasher tokenHasher = new TokenHasher();
  private PasswordResetService service;

  @BeforeEach
  void setUp() {
    ResendProperties resendProperties = new ResendProperties(null, null, null, 24, 1, 5);
    service =
        new PasswordResetService(
            userRepository,
            passwordResetTokenRepository,
            passwordEncoder,
            tokenHasher,
            refreshTokenFamilyRevoker,
            loginAttemptService,
            emailRateLimiter,
            tokenDenylistService,
            eventPublisher,
            resendProperties);
  }

  @Test
  void resetPasswordUpdatesHashAndInvalidatesAllSessionsAndAccessTokens() {
    UUID userId = UUID.randomUUID();
    User user = new User();
    user.setId(userId);
    user.setEmail("lawyer@example.com");
    user.setStatus(UserStatus.ACTIVE);
    PasswordResetToken token =
        resetToken(userId, null, LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
    when(passwordResetTokenRepository.findByTokenHash(tokenHasher.sha256Hex(RAW_TOKEN)))
        .thenReturn(Optional.of(token));
    when(passwordResetTokenRepository.consume(any(), any())).thenReturn(1);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_HASH);

    service.resetPassword(RAW_TOKEN, NEW_PASSWORD);

    assertThat(user.getPasswordHash()).isEqualTo(NEW_HASH);
    verify(passwordResetTokenRepository).consume(any(), any());
    verify(refreshTokenFamilyRevoker).revokeAllActive(userId);
    verify(tokenDenylistService).revokeAccessTokensFor(userId);
    verify(loginAttemptService).reset(user.getEmail());
  }

  @Test
  void resetPasswordThrowsForUnknownToken() {
    when(passwordResetTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, NEW_PASSWORD))
        .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(refreshTokenFamilyRevoker, never()).revokeAllActive(any());
    verify(tokenDenylistService, never()).revokeAccessTokensFor(any());
  }

  @Test
  void resetPasswordThrowsForAlreadyUsedToken() {
    UUID userId = UUID.randomUUID();
    PasswordResetToken token =
        resetToken(
            userId,
            LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5),
            LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
    when(passwordResetTokenRepository.findByTokenHash(tokenHasher.sha256Hex(RAW_TOKEN)))
        .thenReturn(Optional.of(token));
    when(passwordResetTokenRepository.consume(any(), any())).thenReturn(0);

    assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, NEW_PASSWORD))
        .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(refreshTokenFamilyRevoker, never()).revokeAllActive(any());
    verify(userRepository, never()).findById(any());
  }

  @Test
  void resetPasswordThrowsForExpiredToken() {
    UUID userId = UUID.randomUUID();
    PasswordResetToken token =
        resetToken(userId, null, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    when(passwordResetTokenRepository.findByTokenHash(tokenHasher.sha256Hex(RAW_TOKEN)))
        .thenReturn(Optional.of(token));
    when(passwordResetTokenRepository.consume(any(), any())).thenReturn(0);

    assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, NEW_PASSWORD))
        .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(refreshTokenFamilyRevoker, never()).revokeAllActive(any());
  }

  @Test
  void resetPasswordThrowsWhenAccountIsNoLongerActive() {
    UUID userId = UUID.randomUUID();
    User rejected = new User();
    rejected.setId(userId);
    rejected.setEmail("lawyer@example.com");
    rejected.setStatus(UserStatus.REJECTED);
    PasswordResetToken token =
        resetToken(userId, null, LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
    when(passwordResetTokenRepository.findByTokenHash(tokenHasher.sha256Hex(RAW_TOKEN)))
        .thenReturn(Optional.of(token));
    when(passwordResetTokenRepository.consume(any(), any())).thenReturn(1);
    when(userRepository.findById(userId)).thenReturn(Optional.of(rejected));

    assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, NEW_PASSWORD))
        .isInstanceOf(InvalidPasswordResetTokenException.class);

    verify(refreshTokenFamilyRevoker, never()).revokeAllActive(any());
    verify(tokenDenylistService, never()).revokeAccessTokensFor(any());
  }

  @Test
  void requestResetDoesNothingWhenRateLimited() {
    when(emailRateLimiter.allow(eq("password-reset"), any())).thenReturn(false);

    service.requestReset("lawyer@example.com");

    verify(userRepository, never()).findByEmailAndStatus(any(), any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  private PasswordResetToken resetToken(
      UUID userId, LocalDateTime usedAt, LocalDateTime expiresAt) {
    PasswordResetToken token = new PasswordResetToken();
    token.setUserId(userId);
    token.setUsedAt(usedAt);
    token.setExpiresAt(expiresAt);
    return token;
  }
}
