package com.pravoos.user.service;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.event.PasswordResetRequestedEvent;
import com.pravoos.user.exception.InvalidPasswordResetTokenException;
import com.pravoos.user.model.entity.PasswordResetToken;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.PasswordResetTokenRepository;
import com.pravoos.user.repository.UserRepository;
import com.pravoos.user.security.TokenHasher;
import com.pravoos.user.util.EmailMasker;
import com.pravoos.user.util.EmailNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenHasher tokenHasher;
    private final RefreshTokenFamilyRevoker refreshTokenFamilyRevoker;
    private final LoginAttemptService loginAttemptService;
    private final EmailRateLimiter emailRateLimiter;
    private final TokenDenylistService tokenDenylistService;
    private final ApplicationEventPublisher eventPublisher;
    private final ResendProperties resendProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository passwordResetTokenRepository,
                                PasswordEncoder passwordEncoder,
                                TokenHasher tokenHasher,
                                RefreshTokenFamilyRevoker refreshTokenFamilyRevoker,
                                LoginAttemptService loginAttemptService,
                                EmailRateLimiter emailRateLimiter,
                                TokenDenylistService tokenDenylistService,
                                ApplicationEventPublisher eventPublisher,
                                ResendProperties resendProperties) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.refreshTokenFamilyRevoker = refreshTokenFamilyRevoker;
        this.loginAttemptService = loginAttemptService;
        this.emailRateLimiter = emailRateLimiter;
        this.tokenDenylistService = tokenDenylistService;
        this.eventPublisher = eventPublisher;
        this.resendProperties = resendProperties;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        if (!emailRateLimiter.allow("password-reset", email)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE).ifPresentOrElse(user -> {
            passwordResetTokenRepository.invalidateActiveByUserId(user.getId(), now);

            String rawToken = generateRawToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            token.setTokenHash(tokenHasher.sha256Hex(rawToken));
            token.setExpiresAt(now.plusHours(resendProperties.passwordResetExpiryHours()));
            passwordResetTokenRepository.save(token);

            eventPublisher.publishEvent(new PasswordResetRequestedEvent(email, rawToken));
            log.info("Password reset requested for {}", EmailMasker.mask(email));
        }, () -> log.info("Password reset requested for unknown/inactive email: {}", EmailMasker.mask(email)));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHasher.sha256Hex(rawToken))
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidPasswordResetTokenException();
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        refreshTokenFamilyRevoker.revokeAllActive(user.getId());
        tokenDenylistService.revokeAccessTokensFor(user.getId());
        loginAttemptService.reset(user.getEmail());

        log.info("Password reset completed for {}", EmailMasker.mask(user.getEmail()));
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now(ZoneOffset.UTC));
        if (deleted > 0) {
            log.info("Purged {} expired password reset tokens", deleted);
        }
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
