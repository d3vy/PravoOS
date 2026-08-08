package com.pravoos.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

class GatewayCorsConfigurationTest {

  private final GatewayCorsConfiguration configuration = new GatewayCorsConfiguration();

  @Test
  void includesLocalhostOrigins_onlyWhenFlagEnabled() {
    CorsConfiguration withLocalhost = resolvedConfig(deployProperties(null, null), "", true);
    CorsConfiguration withoutLocalhost = resolvedConfig(deployProperties(null, null), "", false);

    assertThat(withLocalhost.getAllowedOrigins())
        .contains(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173");
    assertThat(withoutLocalhost.getAllowedOrigins()).isEmpty();
  }

  @Test
  void addsHttpsOriginForServerDomain() {
    CorsConfiguration config = resolvedConfig(deployProperties(null, "app.example.com"), "", false);

    assertThat(config.getAllowedOrigins()).containsExactly("https://app.example.com");
  }

  @Test
  void addsHttpAndHttpsOriginsForServerIp() {
    CorsConfiguration config = resolvedConfig(deployProperties("203.0.113.7", null), "", false);

    assertThat(config.getAllowedOrigins())
        .containsExactly("http://203.0.113.7", "https://203.0.113.7");
  }

  @Test
  void mergesOverrideOrigins_deduplicatedAndTrimmed() {
    CorsConfiguration config =
        resolvedConfig(
            deployProperties(null, "app.example.com"),
            " https://app.example.com , https://extra.example.com ,,",
            false);

    assertThat(config.getAllowedOrigins())
        .containsExactly("https://app.example.com", "https://extra.example.com");
  }

  @Test
  void allowsCredentialsAndExposesTotalCountHeader() {
    CorsConfiguration config = resolvedConfig(deployProperties(null, null), "", false);

    assertThat(config.getAllowCredentials()).isTrue();
    assertThat(config.getExposedHeaders()).containsExactly("X-Total-Count");
    assertThat(config.getAllowedMethods())
        .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  }

  private DeployProperties deployProperties(String serverIp, String serverDomain) {
    return new DeployProperties(serverIp, serverDomain);
  }

  private CorsConfiguration resolvedConfig(
      DeployProperties deploy, String allowedOriginsOverride, boolean includeLocalhost) {
    CorsWebFilter filter =
        configuration.corsWebFilter(deploy, allowedOriginsOverride, includeLocalhost);
    CorsConfigurationSource source = extractSource(filter);
    return source.getCorsConfiguration(
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/cases")));
  }

  private CorsConfigurationSource extractSource(CorsWebFilter filter) {
    try {
      Field field = CorsWebFilter.class.getDeclaredField("configSource");
      field.setAccessible(true);
      return (CorsConfigurationSource) field.get(filter);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
