package com.pravoos.ai.shared.court.arbitr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArbitrApiResponse(
    @JsonProperty("Success") Integer success,
    String error,
    @JsonProperty("error_code") Integer errorCode,
    @JsonProperty("Cases") List<Case> cases) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Case(
      @JsonProperty("CaseId") String caseId,
      @JsonProperty("CaseNumber") String caseNumber,
      @JsonProperty("State") String state,
      @JsonProperty("Finished") Boolean finished,
      @JsonProperty("Sides") List<Side> sides,
      @JsonProperty("CaseInstances") List<CaseInstance> caseInstances,
      @JsonProperty("CourtHearings") List<CourtHearing> courtHearings) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Side(@JsonProperty("Name") String name, @JsonProperty("Type") String type) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CaseInstance(
      @JsonProperty("Court") Court court,
      @JsonProperty("Judge") Judge judge,
      @JsonProperty("InstanceEvents") List<InstanceEvent> instanceEvents) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Court(@JsonProperty("Name") String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Judge(@JsonProperty("Name") String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record InstanceEvent(
      @JsonProperty("Id") String id,
      @JsonProperty("EventTypeName") String eventTypeName,
      @JsonProperty("EventContentTypeName") String eventContentTypeName,
      @JsonProperty("AdditionalInfo") String additionalInfo,
      @JsonProperty("Date") String date) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CourtHearing(
      @JsonProperty("Start") String start, @JsonProperty("Judge") Judge judge) {}
}
