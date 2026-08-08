package com.pravoos.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.user.billing.internal.config.YooKassaProperties;
import com.pravoos.user.billing.internal.security.WebhookIpAllowlist;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WebhookIpAllowlistTest {

  private static final List<String> YOOKASSA_RANGES = List.of("185.71.76.0/27", "77.75.156.11");

  @Test
  void allowsAddressInsideCidrRange() {
    assertThat(allowlist(YOOKASSA_RANGES).permits(requestFrom("185.71.76.5", null))).isTrue();
  }

  @Test
  void allowsExactAddressWithoutPrefix() {
    assertThat(allowlist(YOOKASSA_RANGES).permits(requestFrom("77.75.156.11", null))).isTrue();
  }

  @Test
  void rejectsAddressOutsideCidrRange() {
    assertThat(allowlist(YOOKASSA_RANGES).permits(requestFrom("185.71.76.32", null))).isFalse();
  }

  @Test
  void usesGatewaySuppliedClientIpBehindProxies() {
    assertThat(allowlist(YOOKASSA_RANGES).permits(requestFrom("10.0.0.7", null, "185.71.76.5")))
        .isTrue();
    assertThat(allowlist(YOOKASSA_RANGES).permits(requestFrom("10.0.0.7", null, "8.8.8.8")))
        .isFalse();
  }

  @Test
  void ignoresClientSuppliedForwardedForHeader() {
    assertThat(
            allowlist(YOOKASSA_RANGES)
                .permits(requestFrom("10.0.0.7", "185.71.76.5, 10.0.0.3", "8.8.8.8")))
        .isFalse();
    assertThat(allowlist(YOOKASSA_RANGES).permits(requestFrom("10.0.0.7", "185.71.76.5", null)))
        .isFalse();
  }

  @Test
  void emptyAllowlistPermitsEveryone() {
    assertThat(allowlist(List.of()).permits(requestFrom("8.8.8.8", null))).isTrue();
  }

  private WebhookIpAllowlist allowlist(List<String> allowedIps) {
    return new WebhookIpAllowlist(
        new YooKassaProperties("shop", "secret", "http://localhost/billing", 30, allowedIps));
  }

  private MockHttpServletRequest requestFrom(String remoteAddr, String forwardedFor) {
    return requestFrom(remoteAddr, forwardedFor, null);
  }

  private MockHttpServletRequest requestFrom(
      String remoteAddr, String forwardedFor, String gatewayClientIp) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    if (forwardedFor != null) {
      request.addHeader("X-Forwarded-For", forwardedFor);
    }
    if (gatewayClientIp != null) {
      request.addHeader("X-Client-Ip", gatewayClientIp);
    }
    return request;
  }
}
