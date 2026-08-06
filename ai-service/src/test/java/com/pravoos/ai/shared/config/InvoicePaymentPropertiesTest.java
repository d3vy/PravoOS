package com.pravoos.ai.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InvoicePaymentPropertiesTest {

  @Test
  void nullWebhookAllowedIpsBecomesEmptyList() {
    InvoicePaymentProperties properties =
        new InvoicePaymentProperties("shop", "secret", "https://return", null);

    assertThat(properties.webhookAllowedIps()).isEmpty();
  }

  @Test
  void webhookAllowedIpsIsDefensivelyCopied() {
    List<String> mutable = new java.util.ArrayList<>(List.of("1.2.3.4"));
    InvoicePaymentProperties properties =
        new InvoicePaymentProperties("shop", "secret", "https://return", mutable);
    mutable.add("5.6.7.8");

    assertThat(properties.webhookAllowedIps()).containsExactly("1.2.3.4");
  }

  @Test
  void configuredIsFalseWhenShopIdMissing() {
    InvoicePaymentProperties properties =
        new InvoicePaymentProperties(null, "secret", "https://return", List.of());

    assertThat(properties.configured()).isFalse();
  }

  @Test
  void configuredIsFalseWhenSecretKeyBlank() {
    InvoicePaymentProperties properties =
        new InvoicePaymentProperties("shop", "   ", "https://return", List.of());

    assertThat(properties.configured()).isFalse();
  }

  @Test
  void configuredIsTrueWhenShopIdAndSecretKeyPresent() {
    InvoicePaymentProperties properties =
        new InvoicePaymentProperties("shop", "secret", "https://return", List.of());

    assertThat(properties.configured()).isTrue();
  }
}
