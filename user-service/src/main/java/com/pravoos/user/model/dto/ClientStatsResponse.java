package com.pravoos.user.model.dto;

public record ClientStatsResponse(
        long newThisWeek,
        long totalActive
) {}
