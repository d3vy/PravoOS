package com.pravoos.ai.shared.signature;

import java.time.LocalDateTime;

public record CmsSignatureDetails(
        String signerCommonName,
        String certificateSubject,
        String certificateIssuer,
        String certificateSerial,
        LocalDateTime certificateValidFrom,
        LocalDateTime certificateValidTo,
        String signatureAlgorithm,
        LocalDateTime signingTime
) {
}
