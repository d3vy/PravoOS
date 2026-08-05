package com.pravoos.user.privacy.internal.service;

import com.pravoos.user.identity.api.TokenDenylistService;
import com.pravoos.user.privacy.api.ConsentRecorder;
import com.pravoos.user.privacy.api.SignupConsent;
import com.pravoos.user.privacy.internal.config.PrivacyProperties;
import com.pravoos.user.privacy.internal.dto.ConsentResponse;
import com.pravoos.user.privacy.internal.model.entity.UserConsent;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import com.pravoos.user.privacy.internal.repository.UserConsentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService implements ConsentRecorder {

  private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

  private final UserConsentRepository consentRepository;
  private final PrivacyProperties privacyProperties;
  private final TokenDenylistService tokenDenylistService;

  public ConsentService(
      UserConsentRepository consentRepository,
      PrivacyProperties privacyProperties,
      TokenDenylistService tokenDenylistService) {
    this.consentRepository = consentRepository;
    this.privacyProperties = privacyProperties;
    this.tokenDenylistService = tokenDenylistService;
  }

  @Override
  @Transactional
  public void recordSignupConsent(UUID userId, SignupConsent consent) {
    String version =
        consent.policyVersion() == null || consent.policyVersion().isBlank()
            ? privacyProperties.resolvedPolicyVersion()
            : consent.policyVersion();
    LocalDateTime grantedAt =
        consent.grantedAt() == null ? LocalDateTime.now() : consent.grantedAt();

    grant(userId, ConsentPurpose.PERSONAL_DATA, version, grantedAt, consent, "SIGNUP");
    if (consent.crossBorderAccepted()) {
      grant(userId, ConsentPurpose.CROSS_BORDER_TRANSFER, version, grantedAt, consent, "SIGNUP");
    }
    if (consent.marketingAccepted()) {
      grant(userId, ConsentPurpose.MARKETING, version, grantedAt, consent, "SIGNUP");
    }
    log.info("Signup consent recorded for user {} (policy v{})", userId, version);
  }

  @Transactional(readOnly = true)
  public List<ConsentResponse> list(UUID userId) {
    return consentRepository.findByUserIdOrderByGrantedAtDesc(userId).stream()
        .map(ConsentResponse::from)
        .toList();
  }

  @Transactional
  public ConsentResponse grant(
      UUID userId, ConsentPurpose purpose, String ipAddress, String userAgent) {
    UserConsent existing =
        consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(userId, purpose).orElse(null);
    if (existing != null
        && existing.getPolicyVersion().equals(privacyProperties.resolvedPolicyVersion())) {
      return ConsentResponse.from(existing);
    }
    if (existing != null) {
      existing.revoke();
      consentRepository.saveAndFlush(existing);
    }
    UserConsent consent =
        persist(
            userId,
            purpose,
            privacyProperties.resolvedPolicyVersion(),
            LocalDateTime.now(),
            ipAddress,
            userAgent,
            "SETTINGS");
    log.info("Consent {} granted by user {}", purpose, userId);
    refreshClaimsIfConsentAffectsThem(userId, purpose);
    return ConsentResponse.from(consent);
  }

  @Transactional
  public boolean revoke(UUID userId, ConsentPurpose purpose) {
    return consentRepository
        .findByUserIdAndPurposeAndRevokedAtIsNull(userId, purpose)
        .map(
            consent -> {
              consent.revoke();
              log.info("Consent {} revoked by user {}", purpose, userId);
              refreshClaimsIfConsentAffectsThem(userId, purpose);
              return true;
            })
        .orElse(false);
  }

  private void refreshClaimsIfConsentAffectsThem(UUID userId, ConsentPurpose purpose) {
    if (purpose == ConsentPurpose.CROSS_BORDER_TRANSFER) {
      tokenDenylistService.revokeAccessTokensFor(userId);
    }
  }

  private void grant(
      UUID userId,
      ConsentPurpose purpose,
      String version,
      LocalDateTime grantedAt,
      SignupConsent consent,
      String source) {
    if (consentRepository.findByUserIdAndPurposeAndRevokedAtIsNull(userId, purpose).isPresent()) {
      return;
    }
    persist(userId, purpose, version, grantedAt, consent.ipAddress(), consent.userAgent(), source);
  }

  private UserConsent persist(
      UUID userId,
      ConsentPurpose purpose,
      String version,
      LocalDateTime grantedAt,
      String ipAddress,
      String userAgent,
      String source) {
    UserConsent consent = new UserConsent();
    consent.setUserId(userId);
    consent.setPurpose(purpose);
    consent.setPolicyVersion(version);
    consent.setGrantedAt(grantedAt);
    consent.setIpAddress(ipAddress);
    consent.setUserAgent(truncate(userAgent));
    consent.setSource(source);
    return consentRepository.save(consent);
  }

  private String truncate(String userAgent) {
    if (userAgent == null) {
      return null;
    }
    return userAgent.length() <= 512 ? userAgent : userAgent.substring(0, 512);
  }
}
