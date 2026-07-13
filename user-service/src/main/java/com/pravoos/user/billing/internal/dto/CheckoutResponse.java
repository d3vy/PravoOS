package com.pravoos.user.billing.internal.dto;

import java.util.UUID;

public record CheckoutResponse(UUID paymentId, String confirmationUrl) {
}
