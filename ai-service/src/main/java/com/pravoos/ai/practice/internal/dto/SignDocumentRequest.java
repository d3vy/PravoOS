package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignDocumentRequest(
        @NotBlank @Size(max = 300) String signerName,
        @AssertTrue(message = "Требуется согласие на подписание документа") boolean consent
) {}
