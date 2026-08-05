package com.pravoos.ai.shared.court;

import java.time.LocalDate;
import java.util.List;

public record CourtCaseData(
    String caseNumber,
    String caseGuid,
    LocalDate nextHearingDate,
    String judgeName,
    List<CourtParty> parties,
    List<CourtEvent> events) {

  public record CourtEvent(
      String sourceEventId, LocalDate date, String type, String description, String courtName) {}

  public record CourtParty(String name, String role) {}
}
