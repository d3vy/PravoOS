package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.EmailDirection;
import com.pravoos.ai.shared.model.enums.EmailLinkSource;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "email_messages")
public class EmailMessage {

  private static final int MAX_MESSAGE_ID_LENGTH = 500;
  private static final int MAX_SUBJECT_LENGTH = 1000;
  private static final int MAX_ADDRESS_LENGTH = 320;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "mailbox_id", nullable = false)
  private UUID mailboxId;

  @Column(name = "message_id", nullable = false, length = MAX_MESSAGE_ID_LENGTH)
  private String messageId;

  @Column(name = "imap_uid", nullable = false)
  private long imapUid;

  @Column(name = "thread_key", length = MAX_MESSAGE_ID_LENGTH)
  private String threadKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private EmailDirection direction;

  @Convert(converter = PiiStringConverter.class)
  @Column(name = "from_address", columnDefinition = "TEXT")
  private String fromAddress;

  @Convert(converter = PiiStringConverter.class)
  @Column(name = "to_addresses", columnDefinition = "TEXT")
  private String toAddresses;

  @Convert(converter = PiiStringConverter.class)
  @Column(name = "cc_addresses", columnDefinition = "TEXT")
  private String ccAddresses;

  @Convert(converter = PiiStringConverter.class)
  @Column(columnDefinition = "TEXT")
  private String subject;

  @Convert(converter = PiiStringConverter.class)
  @Column(name = "body_text", columnDefinition = "TEXT")
  private String bodyText;

  @Column(name = "sent_at")
  private LocalDateTime sentAt;

  @Column(name = "has_attachments", nullable = false)
  private boolean hasAttachments;

  @Column(name = "attachment_count", nullable = false)
  private int attachmentCount;

  @Column(name = "case_id")
  private UUID caseId;

  @Column(name = "client_id")
  private UUID clientId;

  @Enumerated(EnumType.STRING)
  @Column(name = "link_source", length = 20)
  private EmailLinkSource linkSource;

  @Column(name = "linked_at")
  private LocalDateTime linkedAt;

  @Column(name = "client_contact_id")
  private UUID clientContactId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "auto_link_attempts", nullable = false)
  private int autoLinkAttempts;

  protected EmailMessage() {}

  public EmailMessage(UUID mailboxId, String messageId, long imapUid, EmailDirection direction) {
    this.mailboxId = mailboxId;
    this.messageId = truncate(messageId, MAX_MESSAGE_ID_LENGTH);
    this.imapUid = imapUid;
    this.direction = direction;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void linkToCase(UUID caseId) {
    this.caseId = caseId;
  }

  public void linkToClient(UUID clientId) {
    this.clientId = clientId;
  }

  public void applyLink(UUID caseId, UUID clientId, EmailLinkSource linkSource) {
    this.caseId = caseId;
    this.clientId = clientId;
    this.linkSource = linkSource;
    this.linkedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void clearLink() {
    this.caseId = null;
    this.clientId = null;
    this.linkSource = null;
    this.linkedAt = null;
    this.clientContactId = null;
  }

  public void attachClientContact(UUID clientContactId) {
    this.clientContactId = clientContactId;
  }

  public boolean isLinked() {
    return caseId != null || clientId != null;
  }

  public void recordAutoLinkAttempt() {
    autoLinkAttempts++;
  }

  public int getAutoLinkAttempts() {
    return autoLinkAttempts;
  }

  private static String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  public UUID getId() {
    return id;
  }

  public UUID getMailboxId() {
    return mailboxId;
  }

  public String getMessageId() {
    return messageId;
  }

  public long getImapUid() {
    return imapUid;
  }

  public String getThreadKey() {
    return threadKey;
  }

  public void setThreadKey(String threadKey) {
    this.threadKey = truncate(threadKey, MAX_MESSAGE_ID_LENGTH);
  }

  public EmailDirection getDirection() {
    return direction;
  }

  public String getFromAddress() {
    return fromAddress;
  }

  public void setFromAddress(String fromAddress) {
    this.fromAddress = truncate(fromAddress, MAX_ADDRESS_LENGTH);
  }

  public String getToAddresses() {
    return toAddresses;
  }

  public void setToAddresses(String toAddresses) {
    this.toAddresses = toAddresses;
  }

  public String getCcAddresses() {
    return ccAddresses;
  }

  public void setCcAddresses(String ccAddresses) {
    this.ccAddresses = ccAddresses;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = truncate(subject, MAX_SUBJECT_LENGTH);
  }

  public String getBodyText() {
    return bodyText;
  }

  public void setBodyText(String bodyText) {
    this.bodyText = bodyText;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(LocalDateTime sentAt) {
    this.sentAt = sentAt;
  }

  public boolean isHasAttachments() {
    return hasAttachments;
  }

  public void setHasAttachments(boolean hasAttachments) {
    this.hasAttachments = hasAttachments;
  }

  public int getAttachmentCount() {
    return attachmentCount;
  }

  public void setAttachmentCount(int attachmentCount) {
    this.attachmentCount = attachmentCount;
  }

  public UUID getCaseId() {
    return caseId;
  }

  public UUID getClientId() {
    return clientId;
  }

  public EmailLinkSource getLinkSource() {
    return linkSource;
  }

  public LocalDateTime getLinkedAt() {
    return linkedAt;
  }

  public UUID getClientContactId() {
    return clientContactId;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
