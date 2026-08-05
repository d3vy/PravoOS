package com.pravoos.user.privacy.internal.model.entity;

import com.pravoos.user.privacy.internal.model.enums.SubjectRequestStatus;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subject_requests")
public class SubjectRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "subject_ref", nullable = false)
  private String subjectRef;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private SubjectRequestType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SubjectRequestStatus status = SubjectRequestStatus.PENDING;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "due_at", nullable = false)
  private LocalDateTime dueAt;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column private String note;

  @PrePersist
  void prePersist() {
    if (requestedAt == null) {
      requestedAt = LocalDateTime.now();
    }
  }

  public void complete(String resultNote) {
    status = SubjectRequestStatus.COMPLETED;
    completedAt = LocalDateTime.now();
    note = resultNote;
  }

  public void reject(String reason) {
    status = SubjectRequestStatus.REJECTED;
    completedAt = LocalDateTime.now();
    note = reason;
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

  public String getSubjectRef() {
    return subjectRef;
  }

  public void setSubjectRef(String subjectRef) {
    this.subjectRef = subjectRef;
  }

  public SubjectRequestType getType() {
    return type;
  }

  public void setType(SubjectRequestType type) {
    this.type = type;
  }

  public SubjectRequestStatus getStatus() {
    return status;
  }

  public LocalDateTime getRequestedAt() {
    return requestedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public LocalDateTime getDueAt() {
    return dueAt;
  }

  public void setDueAt(LocalDateTime dueAt) {
    this.dueAt = dueAt;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getNote() {
    return note;
  }
}
