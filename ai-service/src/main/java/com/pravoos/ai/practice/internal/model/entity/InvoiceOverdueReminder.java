package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_overdue_reminders")
public class InvoiceOverdueReminder {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "threshold_days", nullable = false)
  private int thresholdDays;

  @Column(name = "sent_at", nullable = false)
  private LocalDateTime sentAt;

  protected InvoiceOverdueReminder() {}

  public InvoiceOverdueReminder(UUID invoiceId, int thresholdDays, LocalDateTime sentAt) {
    this.invoiceId = invoiceId;
    this.thresholdDays = thresholdDays;
    this.sentAt = sentAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public int getThresholdDays() {
    return thresholdDays;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }
}
