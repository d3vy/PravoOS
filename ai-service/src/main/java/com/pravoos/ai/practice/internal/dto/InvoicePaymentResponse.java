package com.pravoos.ai.practice.internal.dto;

import java.util.UUID;

public record InvoicePaymentResponse(UUID invoiceId, String confirmationUrl) {}
