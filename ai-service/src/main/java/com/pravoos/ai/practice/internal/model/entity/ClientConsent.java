package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "client_consents")
public class ClientConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private UUID lawyerId;

    @Column(nullable = false, length = 20)
    private String policyVersion;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    @Column
    private LocalDateTime revokedAt;

    @PrePersist
    void prePersist() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke() {
        revokedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }

    public UUID getClientId() { return clientId; }
    public void setClientId(UUID clientId) { this.clientId = clientId; }

    public UUID getLawyerId() { return lawyerId; }
    public void setLawyerId(UUID lawyerId) { this.lawyerId = lawyerId; }

    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }

    public LocalDateTime getGrantedAt() { return grantedAt; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
}
