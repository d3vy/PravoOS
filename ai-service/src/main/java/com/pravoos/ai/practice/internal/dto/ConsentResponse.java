package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.ClientConsent;
import java.time.LocalDateTime;

public record ConsentResponse(
    String policyVersion, LocalDateTime grantedAt, LocalDateTime revokedAt, boolean active) {
  public static ConsentResponse from(ClientConsent consent) {
    return new ConsentResponse(
        consent.getPolicyVersion(),
        consent.getGrantedAt(),
        consent.getRevokedAt(),
        consent.isActive());
  }
}
