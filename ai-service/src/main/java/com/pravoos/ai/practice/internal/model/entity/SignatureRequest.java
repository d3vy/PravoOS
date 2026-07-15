package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "signature_requests")
public class SignatureRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private UUID caseId;

    @Column(nullable = false)
    private UUID signerClientId;

    @Column(nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SignatureProviderType provider;

    @Column(length = 200)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignatureStatus status;

    @Column(nullable = false, length = 64)
    private String documentHash;

    @Column(length = 1000)
    private String message;

    @Column(length = 300)
    private String signerName;

    @Column
    private UUID signerUserId;

    @Column(length = 64)
    private String signerIp;

    @Column(length = 500)
    private String signerUserAgent;

    @Column(length = 1000)
    private String consentText;

    @Column
    private LocalDateTime signedAt;

    @Column(length = 1000)
    private String declineReason;

    @Column
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @Version
    private long version;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    public UUID getId() { return id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getSignerClientId() { return signerClientId; }
    public void setSignerClientId(UUID signerClientId) { this.signerClientId = signerClientId; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public SignatureProviderType getProvider() { return provider; }
    public void setProvider(SignatureProviderType provider) { this.provider = provider; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public SignatureStatus getStatus() { return status; }
    public void setStatus(SignatureStatus status) { this.status = status; }

    public String getDocumentHash() { return documentHash; }
    public void setDocumentHash(String documentHash) { this.documentHash = documentHash; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSignerName() { return signerName; }
    public void setSignerName(String signerName) { this.signerName = signerName; }

    public UUID getSignerUserId() { return signerUserId; }
    public void setSignerUserId(UUID signerUserId) { this.signerUserId = signerUserId; }

    public String getSignerIp() { return signerIp; }
    public void setSignerIp(String signerIp) { this.signerIp = signerIp; }

    public String getSignerUserAgent() { return signerUserAgent; }
    public void setSignerUserAgent(String signerUserAgent) { this.signerUserAgent = signerUserAgent; }

    public String getConsentText() { return consentText; }
    public void setConsentText(String consentText) { this.consentText = consentText; }

    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }

    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
