package com.pravoos.ai.practice.internal.dto;

import java.time.LocalDate;
import java.util.List;

public record CaseTimelineStats(
        int hearingCount,
        LocalDate firstEventDate,
        LocalDate lastEventDate,
        Integer spanDays,
        Integer averageIntervalDays,
        List<String> courts,
        List<EventTypeCount> eventTypes,
        LocalDate nextHearingDate,
        Integer daysToNextHearing,
        String judge,
        List<CasePartyDto> parties
) {}
