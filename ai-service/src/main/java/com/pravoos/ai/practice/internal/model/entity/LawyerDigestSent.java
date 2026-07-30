package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lawyer_digest_sent")
public class LawyerDigestSent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "lawyer_id", nullable = false)
  private UUID lawyerId;

  @Column(name = "digest_date", nullable = false)
  private LocalDate digestDate;

  @Column(name = "sent_at", nullable = false)
  private LocalDateTime sentAt;

  protected LawyerDigestSent() {}

  public LawyerDigestSent(UUID lawyerId, LocalDate digestDate, LocalDateTime sentAt) {
    this.lawyerId = lawyerId;
    this.digestDate = digestDate;
    this.sentAt = sentAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public LocalDate getDigestDate() {
    return digestDate;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }
}
