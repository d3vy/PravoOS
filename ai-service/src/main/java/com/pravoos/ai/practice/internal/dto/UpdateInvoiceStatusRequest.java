package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInvoiceStatusRequest(
        @NotNull InvoiceStatus status
) {}
