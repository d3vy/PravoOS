package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.Size;

public record DeclineSignatureRequest(@Size(max = 1000) String reason) {}
