package com.pravoos.user.privacy.internal.dto;

import com.pravoos.user.privacy.internal.model.entity.UserConsent;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConsentResponse(
    UUID id,
    ConsentPurpose purpose,
    boolean mandatory,
    String policyVersion,
    LocalDateTime grantedAt,
    LocalDateTime revokedAt,
    String source) {

  public static ConsentResponse from(UserConsent consent) {
    return new ConsentResponse(
        consent.getId(),
        consent.getPurpose(),
        consent.getPurpose().isMandatory(),
        consent.getPolicyVersion(),
        consent.getGrantedAt(),
        consent.getRevokedAt(),
        consent.getSource());
  }
}
