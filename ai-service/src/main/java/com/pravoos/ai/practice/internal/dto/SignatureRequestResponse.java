package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SignatureRequestResponse(
        UUID id,
        UUID documentId,
        UUID caseId,
        SignatureProviderType provider,
        SignatureStatus status,
        String documentHash,
        String message,
        String signerName,
        String signerIp,
        LocalDateTime signedAt,
        String declineReason,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        String certificateSubject,
        String certificateIssuer,
        String certificateSerial,
        LocalDateTime certificateValidFrom,
        LocalDateTime certificateValidTo,
        String signatureAlgorithm,
        LocalDateTime declaredSigningTime,
        boolean hasSignatureFile
) {
    public static SignatureRequestResponse from(SignatureRequest request, LocalDateTime now) {
        return new SignatureRequestResponse(
                request.getId(),
                request.getDocumentId(),
                request.getCaseId(),
                request.getProvider(),
                effectiveStatus(request, now),
                request.getDocumentHash(),
                request.getMessage(),
                request.getSignerName(),
                request.getSignerIp(),
                request.getSignedAt(),
                request.getDeclineReason(),
                request.getExpiresAt(),
                request.getCreatedAt(),
                request.getCertificateSubject(),
                request.getCertificateIssuer(),
                request.getCertificateSerial(),
                request.getCertificateValidFrom(),
                request.getCertificateValidTo(),
                request.getSignatureAlgorithm(),
                request.getDeclaredSigningTime(),
                request.getSignatureData() != null);
    }

    private static SignatureStatus effectiveStatus(SignatureRequest request, LocalDateTime now) {
        if (request.getStatus() == SignatureStatus.PENDING && request.isExpired(now)) {
            return SignatureStatus.EXPIRED;
        }
        return request.getStatus();
    }
}
