package com.pravoos.user.service;

import com.pravoos.user.exception.AccountLockedException;
import com.pravoos.user.exception.InvalidCredentialsException;
import com.pravoos.user.model.dto.LoginRequest;
import com.pravoos.user.model.dto.TokenResponse;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.security.JwtTokenProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "lawyer@example.com";
    private static final String RAW_PASSWORD = "secret123";
    private static final String HASH = "$2a$10$hash";

    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginAttemptService loginAttemptService;

    private AuthService authService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        authService = new AuthService(userRepository, jwtTokenProvider, passwordEncoder,
                refreshTokenService, loginAttemptService, meterRegistry);
    }

    @Test
    void login_succeeds_withValidCredentials() {
        User user = activeUser();
        when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(true);
        when(jwtTokenProvider.generateToken(user.getId(), EMAIL, UserRole.LAWYER)).thenReturn("access");
        when(refreshTokenService.issue(user.getId())).thenReturn("refresh");

        TokenResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASSWORD));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(loginAttemptService).reset(EMAIL);
        assertThat(counter("success")).isEqualTo(1.0);
    }

    @Test
    void login_fails_andRecordsFailure_whenPasswordWrong() {
        User user = activeUser();
        when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure(EMAIL);
        verify(refreshTokenService, never()).issue(any());
        assertThat(counter("failure")).isEqualTo(1.0);
    }

    @Test
    void login_fails_forUnknownUser_withoutLeakingExistence() {
        when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmailAndStatus(EMAIL, UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches(eq(RAW_PASSWORD), anyString());
        verify(loginAttemptService).recordFailure(EMAIL);
        assertThat(counter("failure")).isEqualTo(1.0);
    }

    @Test
    void login_isBlocked_whenAccountLocked() {
        when(loginAttemptService.remainingLockSeconds(EMAIL)).thenReturn(Optional.of(120L));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASSWORD)))
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
