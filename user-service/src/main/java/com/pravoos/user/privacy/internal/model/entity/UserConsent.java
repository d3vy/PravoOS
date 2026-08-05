package com.pravoos.user.privacy.internal.model.entity;

import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_consents")
public class UserConsent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private ConsentPurpose purpose;

  @Column(name = "policy_version", nullable = false, length = 20)
  private String policyVersion;

  @Column(name = "granted_at", nullable = false)
  private LocalDateTime grantedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(nullable = false, length = 40)
  private String source;

  @PrePersist
  void prePersist() {
    if (grantedAt == null) {
      grantedAt = LocalDateTime.now();
    }
    if (source == null) {
      source = "SIGNUP";
    }
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public void revoke() {
    revokedAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public ConsentPurpose getPurpose() {
    return purpose;
  }

  public void setPurpose(ConsentPurpose purpose) {
    this.purpose = purpose;
  }

  public String getPolicyVersion() {
    return policyVersion;
  }

  public void setPolicyVersion(String policyVersion) {
    this.policyVersion = policyVersion;
  }

  public LocalDateTime getGrantedAt() {
    return grantedAt;
  }

  public void setGrantedAt(LocalDateTime grantedAt) {
    this.grantedAt = grantedAt;
  }

  public LocalDateTime getRevokedAt() {
    return revokedAt;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }
}
