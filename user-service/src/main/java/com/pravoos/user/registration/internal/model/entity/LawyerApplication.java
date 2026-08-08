package com.pravoos.user.registration.internal.model.entity;

import com.pravoos.user.registration.internal.model.enums.ApplicationStatus;
import com.pravoos.user.shared.security.PiiStringConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "lawyer_applications")
public class LawyerApplication {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String email;

  @Convert(converter = PiiStringConverter.class)
  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private String passwordHash;

  @Column(length = 255)
  private String specialization;

  @Convert(converter = PiiStringConverter.class)
  @Column
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private ApplicationStatus status;

  @Column(nullable = false)
  private LocalDateTime submittedAt;

  private LocalDateTime reviewedAt;

  private UUID reviewedBy;

  @Column(nullable = false, unique = true, length = 128)
  private String statusToken;

  private LocalDateTime statusTokenExpiresAt;

  @Column(unique = true)
  private String emailVerificationToken;

  private LocalDateTime emailVerificationExpiresAt;

  @Column(nullable = false)
  private boolean emailVerified = false;

  @Column(name = "consent_policy_version", length = 20)
  private String consentPolicyVersion;

  @Column(name = "consent_granted_at")
  private LocalDateTime consentGrantedAt;

  @Column(name = "consent_ip", length = 64)
  private String consentIp;

  @Column(name = "consent_user_agent")
  private String consentUserAgent;

  @Column(name = "consent_cross_border", nullable = false)
  private boolean consentCrossBorder = false;

  @Column(name = "consent_marketing", nullable = false)
  private boolean consentMarketing = false;

  @PrePersist
  void prePersist() {
    submittedAt = LocalDateTime.now(ZoneOffset.UTC);
    if (status == null) {
      status = ApplicationStatus.PENDING;
    }
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getSpecialization() {
    return specialization;
  }

  public void setSpecialization(String specialization) {
    this.specialization = specialization;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public ApplicationStatus getStatus() {
    return status;
  }

  public void setStatus(ApplicationStatus status) {
    this.status = status;
  }

  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public void setReviewedBy(UUID reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public String getStatusToken() {
    return statusToken;
  }

  public void setStatusToken(String statusToken) {
    this.statusToken = statusToken;
  }

  public LocalDateTime getStatusTokenExpiresAt() {
    return statusTokenExpiresAt;
  }

  public void setStatusTokenExpiresAt(LocalDateTime statusTokenExpiresAt) {
    this.statusTokenExpiresAt = statusTokenExpiresAt;
  }

  public String getEmailVerificationToken() {
    return emailVerificationToken;
  }

  public void setEmailVerificationToken(String emailVerificationToken) {
    this.emailVerificationToken = emailVerificationToken;
  }

  public LocalDateTime getEmailVerificationExpiresAt() {
    return emailVerificationExpiresAt;
  }

  public void setEmailVerificationExpiresAt(LocalDateTime emailVerificationExpiresAt) {
    this.emailVerificationExpiresAt = emailVerificationExpiresAt;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public void setEmailVerified(boolean emailVerified) {
    this.emailVerified = emailVerified;
  }

  public String getConsentPolicyVersion() {
    return consentPolicyVersion;
  }

  public void setConsentPolicyVersion(String consentPolicyVersion) {
    this.consentPolicyVersion = consentPolicyVersion;
  }

  public LocalDateTime getConsentGrantedAt() {
    return consentGrantedAt;
  }

  public void setConsentGrantedAt(LocalDateTime consentGrantedAt) {
    this.consentGrantedAt = consentGrantedAt;
  }

  public String getConsentIp() {
    return consentIp;
  }

  public void setConsentIp(String consentIp) {
    this.consentIp = consentIp;
  }

  public String getConsentUserAgent() {
    return consentUserAgent;
  }

  public void setConsentUserAgent(String consentUserAgent) {
    this.consentUserAgent = consentUserAgent;
  }

  public boolean isConsentCrossBorder() {
    return consentCrossBorder;
  }

  public void setConsentCrossBorder(boolean consentCrossBorder) {
    this.consentCrossBorder = consentCrossBorder;
  }

  public boolean isConsentMarketing() {
    return consentMarketing;
  }

  public void setConsentMarketing(boolean consentMarketing) {
    this.consentMarketing = consentMarketing;
  }
}
