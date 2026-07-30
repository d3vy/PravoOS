package com.pravoos.ai.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.billing.yookassa")
public record InvoicePaymentProperties(
    String shopId, String secretKey, String returnUrl, List<String> webhookAllowedIps) {

  public InvoicePaymentProperties {
    webhookAllowedIps = webhookAllowedIps == null ? List.of() : List.copyOf(webhookAllowedIps);
  }

  public boolean configured() {
    return shopId != null && !shopId.isBlank() && secretKey != null && !secretKey.isBlank();
  }
}
