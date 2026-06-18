package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.CaseStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCaseStatusRequest(
        @NotNull CaseStatus status
) {}
