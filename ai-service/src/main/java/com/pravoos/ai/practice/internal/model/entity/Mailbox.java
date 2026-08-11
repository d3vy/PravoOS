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
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "mailboxes")
public class Mailbox {

  private static final int MAX_ERROR_LENGTH = 500;
  private static final int MAX_UNREADABLE_UID_ATTEMPTS = 3;
  private static final Duration MIN_BACKOFF = Duration.ofMinutes(10);
  private static final Duration MAX_BACKOFF = Duration.ofHours(12);
  private static final int MAX_FAILURES_BEFORE_PAUSE = 8;

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
  private int consecutiveFailures = 0;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  private LocalDateTime nextAttemptAt;

  private Long unreadableUid;

  @Column(nullable = false)
  private int unreadableUidAttempts;

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
    consecutiveFailures = 0;
    nextAttemptAt = null;
  }

  public boolean markFailed(String error) {
    status = MailboxStatus.ERROR;
    lastError = truncate(error);
    boolean wasAutoPaused = isAutoPaused();
    consecutiveFailures++;
    if (consecutiveFailures >= MAX_FAILURES_BEFORE_PAUSE) {
      syncEnabled = false;
      nextAttemptAt = null;
      return !wasAutoPaused;
    }
    nextAttemptAt = LocalDateTime.now(ZoneOffset.UTC).plus(retryBackoff());
    return false;
  }

  public boolean isAutoPaused() {
    return !syncEnabled && consecutiveFailures >= MAX_FAILURES_BEFORE_PAUSE;
  }

  public void markVerificationFailed(String error) {
    status = MailboxStatus.ERROR;
    lastError = truncate(error);
  }

  public boolean isRetryPending(LocalDateTime now) {
    return nextAttemptAt != null && nextAttemptAt.isAfter(now);
  }

  public boolean registerUnreadableUid(long uid) {
    if (unreadableUid != null && unreadableUid == uid) {
      unreadableUidAttempts++;
    } else {
      unreadableUid = uid;
      unreadableUidAttempts = 1;
    }
    return isUnreadableUidExhausted();
  }

  public boolean isUnreadableUidExhausted() {
    return unreadableUid != null && unreadableUidAttempts >= MAX_UNREADABLE_UID_ATTEMPTS;
  }

  public void clearUnreadableUid() {
    unreadableUid = null;
    unreadableUidAttempts = 0;
  }

  public void resetVerification() {
    status = MailboxStatus.PENDING;
    lastError = null;
    consecutiveFailures = 0;
    nextAttemptAt = null;
    clearUnreadableUid();
  }

  public void resetSyncCursor() {
    uidValidity = null;
    lastSeenUid = null;
    clearUnreadableUid();
  }

  private Duration retryBackoff() {
    Duration backoff = MIN_BACKOFF.multipliedBy(1L << Math.min(consecutiveFailures - 1, 8));
    return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
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
    if (syncEnabled && !this.syncEnabled) {
      consecutiveFailures = 0;
      nextAttemptAt = null;
    }
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

  public int getConsecutiveFailures() {
    return consecutiveFailures;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public LocalDateTime getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Long getUnreadableUid() {
    return unreadableUid;
  }

  public int getUnreadableUidAttempts() {
    return unreadableUidAttempts;
  }
}
