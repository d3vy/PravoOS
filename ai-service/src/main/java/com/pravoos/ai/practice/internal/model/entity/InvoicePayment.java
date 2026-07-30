package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.InvoicePaymentStatus;
import jakarta.persistence.Column;
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
@Table(name = "invoice_payments")
public class InvoicePayment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "invoice_id", nullable = false)
  private UUID invoiceId;

  @Column(name = "provider_payment_id", nullable = false, length = 64)
  private String providerPaymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InvoicePaymentStatus status = InvoicePaymentStatus.PENDING;

  @Column(name = "amount_kopecks", nullable = false)
  private long amountKopecks;

  @Column(name = "confirmation_url", columnDefinition = "TEXT")
  private String confirmationUrl;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  protected InvoicePayment() {}

  public InvoicePayment(
      UUID invoiceId, String providerPaymentId, long amountKopecks, String confirmationUrl) {
    this.invoiceId = invoiceId;
    this.providerPaymentId = providerPaymentId;
    this.amountKopecks = amountKopecks;
    this.confirmationUrl = confirmationUrl;
  }

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void markSucceeded() {
    this.status = InvoicePaymentStatus.SUCCEEDED;
    this.paidAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public void markCanceled() {
    this.status = InvoicePaymentStatus.CANCELED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public String getProviderPaymentId() {
    return providerPaymentId;
  }

  public InvoicePaymentStatus getStatus() {
    return status;
  }

  public long getAmountKopecks() {
    return amountKopecks;
  }

  public String getConfirmationUrl() {
    return confirmationUrl;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }
}
