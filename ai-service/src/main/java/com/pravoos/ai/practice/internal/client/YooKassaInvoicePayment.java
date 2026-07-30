package com.pravoos.ai.practice.internal.client;

public record YooKassaInvoicePayment(
    String id, String status, boolean paid, long amountKopecks, String confirmationUrl) {

  public boolean succeeded() {
    return paid && "succeeded".equals(status);
  }

  public boolean canceled() {
    return "canceled".equals(status);
  }
}
