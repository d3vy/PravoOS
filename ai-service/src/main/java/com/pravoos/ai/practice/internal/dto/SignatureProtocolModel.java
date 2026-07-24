package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import java.time.LocalDateTime;
import java.util.UUID;

public record SignatureProtocolModel(
    UUID signatureId,
    String caseTitle,
    String documentTitle,
    String documentHash,
    SignatureProviderType provider,
    String signerName,
    String signerIp,
    String signerUserAgent,
    String consentText,
    LocalDateTime requestedAt,
    LocalDateTime signedAt,
    LocalDateTime declaredSigningTime,
    String certificateSubject,
    String certificateIssuer,
    String certificateSerial,
    LocalDateTime certificateValidFrom,
    LocalDateTime certificateValidTo,
    String signatureAlgorithm) {
  public boolean isQualified() {
    return provider == SignatureProviderType.DETACHED_CMS;
  }
}
