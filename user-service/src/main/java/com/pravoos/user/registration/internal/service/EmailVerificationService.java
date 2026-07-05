package com.pravoos.user.registration.internal.service;
import com.pravoos.user.shared.service.EmailRateLimiter;

import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.registration.internal.event.VerificationEmailRequestedEvent;
import com.pravoos.user.shared.exception.InvalidVerificationTokenException;
import com.pravoos.user.registration.internal.model.entity.LawyerApplication;
import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.registration.internal.repository.LawyerApplicationRepository;
import com.pravoos.user.shared.security.TokenHasher;
import com.pravoos.user.shared.util.EmailMasker;
import com.pravoos.user.shared.util.EmailNormalizer;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LawyerApplicationRepository applicationRepository;
    private final ResendProperties resendProperties;
    private final EmailRateLimiter emailRateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenHasher tokenHasher;

    public EmailVerificationService(LawyerApplicationRepository applicationRepository,
                                    ResendProperties resendProperties,
                                    EmailRateLimiter emailRateLimiter,
                                    ApplicationEventPublisher eventPublisher,
                                    TokenHasher tokenHasher) {
        this.applicationRepository = applicationRepository;
        this.resendProperties = resendProperties;
        this.emailRateLimiter = emailRateLimiter;
        this.eventPublisher = eventPublisher;
        this.tokenHasher = tokenHasher;
    }

    public String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public LocalDateTime tokenExpiry() {
        return LocalDateTime.now(ZoneOffset.UTC).plusHours(resendProperties.verificationExpiryHours());
    }

    @Transactional
    public void verifyToken(String token) {
        LawyerApplication application = applicationRepository.findByEmailVerificationToken(tokenHasher.sha256Hex(token))
                .orElseThrow(InvalidVerificationTokenException::new);

        LocalDateTime expiresAt = application.getEmailVerificationExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidVerificationTokenException();
        }

        application.setEmailVerified(true);
        application.setEmailVerificationToken(null);
        application.setEmailVerificationExpiresAt(null);
        applicationRepository.save(application);
        log.info("Email verified for application {} ({})", application.getId(), EmailMasker.mask(application.getEmail()));
    }

    @Transactional
    public void resendVerification(String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        if (!emailRateLimiter.allow("verification", email)) {
            return;
        }
        applicationRepository.findByEmailAndStatusAndEmailVerifiedFalse(email, ApplicationStatus.PENDING)
                .ifPresent(application -> {
                    String rawToken = generateToken();
                    application.setEmailVerificationToken(tokenHasher.sha256Hex(rawToken));
                    application.setEmailVerificationExpiresAt(tokenExpiry());
                    applicationRepository.save(application);
                    eventPublisher.publishEvent(new VerificationEmailRequestedEvent(email, rawToken));
                    log.info("Verification email resent to {}", EmailMasker.mask(email));
                });
    }

    @Scheduled(cron = "0 0 4 * * *")
    @SchedulerLock(name = "EmailVerificationService_purgeExpiredVerificationTokens", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredVerificationTokens() {
        int cleared = applicationRepository.clearExpiredVerificationTokens(LocalDateTime.now(ZoneOffset.UTC));
        if (cleared > 0) {
            log.info("Cleared {} expired email verification tokens", cleared);
        }
    }
}
