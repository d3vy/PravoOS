package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GlobalSearchResponse(
    List<CaseHit> cases,
    List<ConversationHit> conversations,
    List<DocumentHit> documents,
    List<ClientHit> clients,
    List<InvoiceHit> invoices) {
  public record CaseHit(
      UUID id, String title, CaseStatus status, String statusName, String clientName) {}

  public record ConversationHit(String id, String title) {}

  public record DocumentHit(UUID id, String title, String fileName, UUID caseId, String snippet) {}

  public record ClientHit(UUID id, String name, String email, String phone) {}

  public record InvoiceHit(
      UUID id,
      String number,
      String clientName,
      BigDecimal total,
      String currency,
      InvoiceStatus status,
      String statusName) {}
}
