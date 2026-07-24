package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record InvoiceSummary(
    UUID id,
    UUID clientId,
    String clientName,
    String number,
    InvoiceStatus status,
    String statusLabel,
    LocalDate issueDate,
    LocalDate dueDate,
    String currency,
    BigDecimal total,
    LocalDateTime createdAt) {
  public static InvoiceSummary from(Invoice invoice, String clientName) {
    return new InvoiceSummary(
        invoice.getId(),
        invoice.getClientId(),
        clientName,
        invoice.getNumber(),
        invoice.getStatus(),
        invoice.getStatus().getDisplayName(),
        invoice.getIssueDate(),
        invoice.getDueDate(),
        invoice.getCurrency(),
        invoice.getTotal(),
        invoice.getCreatedAt());
  }
}
