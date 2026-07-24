package com.pravoos.ai.practice.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceExportModel(
    String number,
    String statusLabel,
    LocalDate issueDate,
    LocalDate dueDate,
    String currency,
    ClientBlock client,
    List<LineRow> lines,
    int totalMinutes,
    BigDecimal subtotal,
    BigDecimal total,
    String notes) {
  public record ClientBlock(String name, String type, String inn, String email, String phone) {}

  public record LineRow(
      String description, int minutes, BigDecimal hourlyRate, BigDecimal amount) {}
}
