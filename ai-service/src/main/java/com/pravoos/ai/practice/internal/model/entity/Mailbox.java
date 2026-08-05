package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.MailboxStatus;
import com.pravoos.ai.shared.security.PiiStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "mailboxes")
public class Mailbox {

  private static final int MAX_ERROR_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 320)
  private String emailAddress;

  @Column(nullable = false)
  private String imapHost;

  @Column(nullable = false)
  private int imapPort;

  @Column(name = "imap_ssl", nullable = false)
  private boolean imapSsl = true;

  @Convert(converter = PiiStringConverter.class)
  @Column(name = "password_enc", nullable = false, columnDefinition = "TEXT")
  private String password;

  @Column(nullable = false)
  private String folder = "INBOX";

  @Column(nullable = false)
  private boolean syncEnabled = true;

  private Long uidValidity;

  private Long lastSeenUid;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MailboxStatus status = MailboxStatus.PENDING;

  @Column(columnDefinition = "TEXT")
  private String lastError;

  private LocalDateTime lastSyncAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
    updatedAt = createdAt;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void markConnected() {
    status = MailboxStatus.OK;
    lastError = null;
  }

  public void markFailed(String error) {
    status = MailboxStatus.ERROR;
    lastError = truncate(error);
  }

  public void resetVerification() {
    status = MailboxStatus.PENDING;
    lastError = null;
  }

  private String truncate(String error) {
    if (error == null) {
      return null;
    }
    return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
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

  public String getEmailAddress() {
    return emailAddress;
  }

  public void setEmailAddress(String emailAddress) {
    this.emailAddress = emailAddress;
  }

  public String getImapHost() {
    return imapHost;
  }

  public void setImapHost(String imapHost) {
    this.imapHost = imapHost;
  }

  public int getImapPort() {
    return imapPort;
  }

  public void setImapPort(int imapPort) {
    this.imapPort = imapPort;
  }

  public boolean isImapSsl() {
    return imapSsl;
  }

  public void setImapSsl(boolean imapSsl) {
    this.imapSsl = imapSsl;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getFolder() {
    return folder;
  }

  public void setFolder(String folder) {
    this.folder = folder;
  }

  public boolean isSyncEnabled() {
    return syncEnabled;
  }

  public void setSyncEnabled(boolean syncEnabled) {
    this.syncEnabled = syncEnabled;
  }

  public Long getUidValidity() {
    return uidValidity;
  }

  public void setUidValidity(Long uidValidity) {
    this.uidValidity = uidValidity;
  }

  public Long getLastSeenUid() {
    return lastSeenUid;
  }

  public void setLastSeenUid(Long lastSeenUid) {
    this.lastSeenUid = lastSeenUid;
  }

  public MailboxStatus getStatus() {
    return status;
  }

  public String getLastError() {
    return lastError;
  }

  public LocalDateTime getLastSyncAt() {
    return lastSyncAt;
  }

  public void setLastSyncAt(LocalDateTime lastSyncAt) {
    this.lastSyncAt = lastSyncAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
