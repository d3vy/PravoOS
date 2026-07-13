package com.pravoos.user.billing.internal.client;

public record YooKassaPayment(String id, String status, boolean paid, long amountKopecks, String confirmationUrl) {

    public boolean succeeded() {
        return "succeeded".equals(status) && paid;
    }

    public boolean canceled() {
        return "canceled".equals(status);
    }
}
