package com.pravoos.user.privacy.internal.dto;

import jakarta.validation.constraints.NotBlank;

public record ErasureRequest(@NotBlank String password) {}
