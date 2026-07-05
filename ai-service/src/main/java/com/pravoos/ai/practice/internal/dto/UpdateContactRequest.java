package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.ContactType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateContactRequest(
        @NotNull ContactType type,
        @NotNull LocalDate contactDate,
        String notes
) {}
