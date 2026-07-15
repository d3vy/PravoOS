package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSignatureRequestDto(
        @NotNull UUID documentId,
        SignatureProviderType provider,
        @Size(max = 1000) String message,
        Integer expiresInDays
) {
    public SignatureProviderType providerOrDefault() {
        return provider != null ? provider : SignatureProviderType.SIMPLE;
    }
}
