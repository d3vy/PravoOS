package com.pravoos.user.billing.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.billing.yookassa")
public record YooKassaProperties(
        String shopId,
        String secretKey,
        String returnUrl,
        int periodDays,
        List<String> webhookAllowedIps
) {

    public YooKassaProperties {
        periodDays = periodDays <= 0 ? 30 : periodDays;
        webhookAllowedIps = webhookAllowedIps == null ? List.of() : List.copyOf(webhookAllowedIps);
    }

    public boolean configured() {
        return shopId != null && !shopId.isBlank() && secretKey != null && !secretKey.isBlank();
    }
}
