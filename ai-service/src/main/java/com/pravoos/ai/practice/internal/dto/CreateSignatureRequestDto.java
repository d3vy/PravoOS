package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.SignatureProviderType;
import com.pravoos.ai.shared.model.enums.SignatureSignerRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateSignatureRequestDto(
    @NotNull UUID documentId,
    SignatureProviderType provider,
    SignatureSignerRole signerRole,
    UUID signerLawyerId,
    @Size(max = 1000) String message,
    Integer expiresInDays) {
  public SignatureProviderType providerOrDefault() {
    return provider != null ? provider : SignatureProviderType.SIMPLE;
  }

  public SignatureSignerRole signerRoleOrDefault() {
    return signerRole != null ? signerRole : SignatureSignerRole.CLIENT;
  }
}
