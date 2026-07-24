package com.pravoos.ai.shared.arbitr;

import java.time.LocalDate;
import java.util.List;

public record ArbitrCaseData(
    String caseNumber,
    String caseGuid,
    LocalDate nextHearingDate,
    String judgeName,
    List<ArbitrParty> parties,
    List<ArbitrEvent> events) {
  public record ArbitrEvent(
      String sourceEventId, LocalDate date, String type, String description, String courtName) {}

  public record ArbitrParty(String name, String role) {}
}
