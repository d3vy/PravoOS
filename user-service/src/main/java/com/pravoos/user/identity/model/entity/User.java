package com.pravoos.user.identity.model.entity;

import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private UserStatus status;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "login_alert_email", nullable = false)
  private boolean loginAlertEmail = true;

  @Column(name = "login_alert_telegram", nullable = false)
  private boolean loginAlertTelegram = false;

  @Column(name = "case_message_email", nullable = false)
  private boolean caseMessageEmail = true;

  @Column(name = "case_message_telegram", nullable = false)
  private boolean caseMessageTelegram = false;

  @Column(name = "login_alert_push", nullable = false)
  private boolean loginAlertPush = true;

  @Column(name = "case_message_push", nullable = false)
  private boolean caseMessagePush = true;

  @Column(name = "preferred_language", nullable = false, length = 8)
  private String preferredLanguage = "ru";

  @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
  private LawyerProfile lawyerProfile;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public boolean isLoginAlertEmail() {
    return loginAlertEmail;
  }

  public void setLoginAlertEmail(boolean loginAlertEmail) {
    this.loginAlertEmail = loginAlertEmail;
  }

  public boolean isLoginAlertTelegram() {
    return loginAlertTelegram;
  }

  public void setLoginAlertTelegram(boolean loginAlertTelegram) {
    this.loginAlertTelegram = loginAlertTelegram;
  }

  public boolean isCaseMessageEmail() {
    return caseMessageEmail;
  }

  public void setCaseMessageEmail(boolean caseMessageEmail) {
    this.caseMessageEmail = caseMessageEmail;
  }

  public boolean isCaseMessageTelegram() {
    return caseMessageTelegram;
  }

  public void setCaseMessageTelegram(boolean caseMessageTelegram) {
    this.caseMessageTelegram = caseMessageTelegram;
  }

  public boolean isLoginAlertPush() {
    return loginAlertPush;
  }

  public void setLoginAlertPush(boolean loginAlertPush) {
    this.loginAlertPush = loginAlertPush;
  }

  public boolean isCaseMessagePush() {
    return caseMessagePush;
  }

  public void setCaseMessagePush(boolean caseMessagePush) {
    this.caseMessagePush = caseMessagePush;
  }

  public String getPreferredLanguage() {
    return preferredLanguage;
  }

  public void setPreferredLanguage(String preferredLanguage) {
    this.preferredLanguage = preferredLanguage;
  }

  public LawyerProfile getLawyerProfile() {
    return lawyerProfile;
  }

  public void setLawyerProfile(LawyerProfile lawyerProfile) {
    this.lawyerProfile = lawyerProfile;
  }
}
