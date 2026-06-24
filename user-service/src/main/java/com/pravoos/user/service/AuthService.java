package com.pravoos.user.service;

import com.pravoos.user.exception.AccountLockedException;
import com.pravoos.user.exception.InvalidCredentialsException;
import com.pravoos.user.exception.InvalidRefreshTokenException;
import com.pravoos.user.model.dto.LoginRequest;
import com.pravoos.user.util.EmailNormalizer;
import com.pravoos.user.model.dto.TokenResponse;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.security.JwtTokenProvider;
import com.pravoos.user.util.EmailMasker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter loginLockedCounter;

    public AuthService(UserRepository userRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService,
                       LoginAttemptService loginAttemptService,
                       MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.loginSuccessCounter = Counter.builder("pravoos.login").tag("result", "success").register(meterRegistry);
        this.loginFailureCounter = Counter.builder("pravoos.login").tag("result", "failure").register(meterRegistry);
        this.loginLockedCounter = Counter.builder("pravoos.login").tag("result", "locked").register(meterRegistry);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        loginAttemptService.remainingLockSeconds(email)
                .ifPresent(seconds -> {
                    loginLockedCounter.increment();
                    log.warn("Blocked login attempt for locked account: {}", EmailMasker.mask(email));
                    throw new AccountLockedException(seconds);
                });

        User user = userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE)
                .orElse(null);

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
        loginSuccessCounter.increment();
        log.info("User authenticated: {}", EmailMasker.mask(email));
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        UUID userId = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = refreshTokenService.issue(user.getId());
        return new TokenResponse(accessToken, refreshToken, user.getId(), user.getEmail(), user.getRole());
    }
}
