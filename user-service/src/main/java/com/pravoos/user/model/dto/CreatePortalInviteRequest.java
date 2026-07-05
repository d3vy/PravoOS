package com.pravoos.user.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreatePortalInviteRequest(
        @NotNull UUID clientId,
        @NotNull UUID lawyerId,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 300) String clientName
) {}
