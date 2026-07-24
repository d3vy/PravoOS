package com.pravoos.user.billing.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(@NotBlank @Size(max = 20) String planCode) {}
