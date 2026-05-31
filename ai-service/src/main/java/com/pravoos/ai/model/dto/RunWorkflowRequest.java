package com.pravoos.ai.model.dto;

import jakarta.validation.constraints.Size;

public record RunWorkflowRequest(
        @Size(max = 2000) String question
) {}
