package com.pravoos.user.identity.internal.service;

import com.pravoos.user.identity.internal.config.MfaProperties;
import com.pravoos.user.shared.exception.MfaException;
import com.pravoos.user.shared.exception.ProfileNotFoundException;
import com.pravoos.user.identity.internal.dto.MfaSetupResponse;
import com.pravoos.user.identity.internal.dto.MfaStatusResponse;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.internal.model.entity.UserMfa;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.internal.repository.UserMfaRepository;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.identity.internal.security.TotpGenerator;
import com.pravoos.user.shared.util.EmailMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final UserMfaRepository userMfaRepository;
    private final UserRepository userRepository;
    private final TotpGenerator totpGenerator;
    private final MfaProperties properties;

    public MfaService(UserMfaRepository userMfaRepository,
                      UserRepository userRepository,
                      TotpGenerator totpGenerator,
                      MfaProperties properties) {
        this.userMfaRepository = userMfaRepository;
        this.userRepository = userRepository;
        this.totpGenerator = totpGenerator;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public MfaStatusResponse status(UUID userId) {
        User user = requireUser(userId);
        boolean enabled = userMfaRepository.existsByUserIdAndEnabledTrue(userId);
        return new MfaStatusResponse(enabled, isMandatory(user.getRole()));
    }

    @Transactional
    public MfaSetupResponse setup(UUID userId) {
        User user = requireUser(userId);
        if (userMfaRepository.existsByUserIdAndEnabledTrue(userId)) {
            throw MfaException.alreadyEnabled();
        }

        String secret = totpGenerator.generateSecret();
        UserMfa mfa = userMfaRepository.findByUserId(userId).orElseGet(UserMfa::new);
        mfa.setUserId(userId);
        mfa.setSecret(secret);
        mfa.setEnabled(false);
        mfa.setConfirmedAt(null);
        userMfaRepository.save(mfa);

        log.info("MFA setup initiated for {}", EmailMasker.mask(user.getEmail()));
        return new MfaSetupResponse(secret, totpGenerator.otpAuthUri(secret, user.getEmail(), properties.issuer()));
    }

    @Transactional
    public void enable(UUID userId, String code) {
        UserMfa mfa = userMfaRepository.findByUserId(userId).orElseThrow(MfaException::notPending);
        if (mfa.isEnabled()) {
            throw MfaException.alreadyEnabled();
        }
        if (!totpGenerator.verify(mfa.getSecret(), code)) {
            throw MfaException.invalidCode();
        }
        mfa.setEnabled(true);
        mfa.setConfirmedAt(LocalDateTime.now(ZoneOffset.UTC));
        log.info("MFA enabled for user {}", userId);
    }

    @Transactional
    public void disable(UUID userId, String code) {
        User user = requireUser(userId);
        if (isMandatory(user.getRole())) {
            throw MfaException.mandatory();
        }
        UserMfa mfa = userMfaRepository.findByUserId(userId).orElseThrow(MfaException::notEnabled);
        if (!mfa.isEnabled()) {
            throw MfaException.notEnabled();
        }
        if (!totpGenerator.verify(mfa.getSecret(), code)) {
            throw MfaException.invalidCode();
        }
        userMfaRepository.deleteByUserId(userId);
        log.info("MFA disabled for user {}", userId);
    }

    @Transactional(readOnly = true)
    public boolean isMfaEnabled(UUID userId) {
        return userMfaRepository.existsByUserIdAndEnabledTrue(userId);
    }

    @Transactional(readOnly = true)
    public boolean verifyLoginCode(UUID userId, String code) {
        return userMfaRepository.findByUserId(userId)
                .filter(UserMfa::isEnabled)
                .map(mfa -> totpGenerator.verify(mfa.getSecret(), code))
                .orElse(false);
    }

    private boolean isMandatory(UserRole role) {
        return properties.adminRequired() && role == UserRole.ADMIN;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(ProfileNotFoundException::new);
    }
}
