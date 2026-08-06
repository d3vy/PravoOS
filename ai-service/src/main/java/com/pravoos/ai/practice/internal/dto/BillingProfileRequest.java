package com.pravoos.ai.practice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BillingProfileRequest(
    @NotBlank @Size(max = 500) String name,
    @Size(max = 20) String inn,
    @Size(max = 20) String kpp,
    @Size(max = 20) String ogrn,
    @Size(max = 500) String legalAddress,
    @Size(max = 500) String bankName,
    @Size(max = 20) String bankBic,
    @Size(max = 34) String bankAccount,
    @Size(max = 34) String corrAccount,
    @Size(max = 255) String email,
    @Size(max = 50) String phone) {}
