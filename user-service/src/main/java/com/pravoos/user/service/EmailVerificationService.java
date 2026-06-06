package com.pravoos.user.service;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.exception.InvalidVerificationTokenException;
import com.pravoos.user.model.entity.LawyerApplication;
import com.pravoos.user.repository.LawyerApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LawyerApplicationRepository applicationRepository;
    private final ResendProperties resendProperties;

    public EmailVerificationService(LawyerApplicationRepository applicationRepository,
                                    ResendProperties resendProperties) {
        this.applicationRepository = applicationRepository;
        this.resendProperties = resendProperties;
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
        LawyerApplication application = applicationRepository.findByEmailVerificationToken(token)
                .orElseThrow(InvalidVerificationTokenException::new);

        if (application.getEmailVerificationExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidVerificationTokenException();
        }

        application.setEmailVerified(true);
        application.setEmailVerificationToken(null);
        application.setEmailVerificationExpiresAt(null);
        applicationRepository.save(application);
    }
}
