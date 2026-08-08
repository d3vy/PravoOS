package com.pravoos.ai.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.shared.config.InvoicePaymentProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WebhookIpAllowlistTest {

  private static final List<String> YOOKASSA_RANGE = List.of("185.71.76.0/27");

  @Test
  void permitsCallerInsideAllowedRange() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Client-Ip", "185.71.76.5");

    assertThat(allowlist(YOOKASSA_RANGE).permits(request)).isTrue();
  }

  @Test
  void rejectsCallerOutsideAllowedRange() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Client-Ip", "203.0.113.9");

    assertThat(allowlist(YOOKASSA_RANGE).permits(request)).isFalse();
  }

  @Test
  void ignoresClientSuppliedForwardedForHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Client-Ip", "203.0.113.9");
    request.addHeader("X-Forwarded-For", "185.71.76.5");

    assertThat(allowlist(YOOKASSA_RANGE).permits(request)).isFalse();
  }

  @Test
  void fallsBackToRemoteAddressWhenGatewayHeaderIsAbsent() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("185.71.76.1");
    request.addHeader("X-Forwarded-For", "203.0.113.9");

    assertThat(allowlist(YOOKASSA_RANGE).permits(request)).isTrue();
  }

  @Test
  void permitsEverythingWhenAllowlistIsNotConfigured() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Client-Ip", "203.0.113.9");

    assertThat(allowlist(List.of()).permits(request)).isTrue();
  }

  @Test
  void ignoresMalformedAllowlistEntries() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Client-Ip", "185.71.76.5");

    assertThat(allowlist(List.of("not-an-ip", "185.71.76.0/27")).permits(request)).isTrue();
  }

  private WebhookIpAllowlist allowlist(List<String> allowedIps) {
    return new WebhookIpAllowlist(
        new InvoicePaymentProperties("shop", "secret", "https://pravoos.ru/return", allowedIps));
  }
}
