package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.InvoiceLine;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
    UUID id, String description, int minutes, BigDecimal hourlyRate, BigDecimal amount) {
  public static InvoiceLineResponse from(InvoiceLine line) {
    return new InvoiceLineResponse(
        line.getId(),
        line.getDescription(),
        line.getMinutes(),
        line.getHourlyRate(),
        line.getAmount());
  }
}
