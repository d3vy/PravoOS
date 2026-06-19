package com.pravoos.ai.arbitr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArbitrApiResponse(
        Integer status,
        Boolean found,
        @JsonProperty("Result") Result result
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("CaseInfo") CaseInfo caseInfo,
            @JsonProperty("CaseInstances") List<CaseInstance> caseInstances
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaseInfo(
            @JsonProperty("CaseId") String caseId,
            @JsonProperty("CaseNumber") String caseNumber,
            @JsonProperty("State") String state
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaseInstance(
            @JsonProperty("Court") Court court,
            @JsonProperty("InstanceEvents") List<InstanceEvent> instanceEvents,
            @JsonProperty("CourtHearings") List<CourtHearing> courtHearings
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Court(
            @JsonProperty("Name") String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstanceEvent(
            @JsonProperty("EventTypeName") String eventTypeName,
            @JsonProperty("Date") String date,
            @JsonProperty("PublishDate") String publishDate,
            @JsonProperty("ContentTypes") List<String> contentTypes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourtHearing(
            @JsonProperty("Location") String location,
            @JsonProperty("Start") String start
    ) {}
}
