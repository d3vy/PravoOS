package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID clientId,
    String clientName,
    String number,
    InvoiceStatus status,
    String statusLabel,
    LocalDate issueDate,
    LocalDate dueDate,
    String currency,
    BigDecimal subtotal,
    BigDecimal vatRate,
    BigDecimal vatAmount,
    BigDecimal total,
    String notes,
    List<InvoiceLineResponse> lines,
    LocalDateTime createdAt) {
  public static InvoiceResponse from(Invoice invoice, String clientName) {
    return new InvoiceResponse(
        invoice.getId(),
        invoice.getClientId(),
        clientName,
        invoice.getNumber(),
        invoice.getStatus(),
        invoice.getStatus().getDisplayName(),
        invoice.getIssueDate(),
        invoice.getDueDate(),
        invoice.getCurrency(),
        invoice.getSubtotal(),
        invoice.getVatRate(),
        invoice.getVatAmount(),
        invoice.getTotal(),
        invoice.getNotes(),
        invoice.getLines().stream().map(InvoiceLineResponse::from).toList(),
        invoice.getCreatedAt());
  }
}
