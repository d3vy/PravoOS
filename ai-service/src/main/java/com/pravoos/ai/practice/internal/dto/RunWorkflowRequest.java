package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.Size;

public record RunWorkflowRequest(
        @Size(max = 2000) String question
) {}
