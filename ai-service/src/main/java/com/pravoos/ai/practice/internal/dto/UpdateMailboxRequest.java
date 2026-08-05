package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateMailboxRequest(
    @Size(max = 512) String password,
    @Size(max = 255) String imapHost,
    @Min(1) @Max(65535) Integer imapPort,
    Boolean imapSsl,
    @Size(max = 255) String folder,
    Boolean syncEnabled) {}
