package com.pravoos.ai.model.dto;

import com.pravoos.ai.model.enums.ContactType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateContactRequest(
        @NotNull ContactType type,
        @NotNull LocalDate contactDate,
        String notes
) {}
