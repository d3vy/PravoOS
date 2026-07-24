package com.pravoos.gateway.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class GatewayCorsConfiguration {

  private static final List<String> LOCAL_DEV_ORIGINS =
      List.of(
          "http://localhost:3000",
          "http://localhost:5173",
          "http://127.0.0.1:3000",
          "http://127.0.0.1:5173");

  @Bean
  public CorsWebFilter corsWebFilter(
      DeployProperties deploy,
      @Value("${ALLOWED_ORIGINS:}") String allowedOriginsOverride,
      @Value("${CORS_INCLUDE_LOCALHOST:false}") boolean includeLocalhostOrigins) {
    CorsConfiguration corsConfig = new CorsConfiguration();
    corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    corsConfig.setAllowedHeaders(List.of("*"));
    corsConfig.setExposedHeaders(List.of("X-Total-Count"));
    corsConfig.setAllowCredentials(true);
    corsConfig.setMaxAge(3600L);

    LinkedHashSet<String> origins = new LinkedHashSet<>();
    if (includeLocalhostOrigins) {
      origins.addAll(LOCAL_DEV_ORIGINS);
    }
    if (deploy.hasServerDomain()) {
      String domain = deploy.serverDomain().trim();
      origins.add("https://" + domain);
    }
    if (deploy.hasServerIp()) {
      String ip = deploy.serverIp().trim();
      origins.add("http://" + ip);
      origins.add("https://" + ip);
    }
    if (!allowedOriginsOverride.isBlank()) {
      Arrays.stream(allowedOriginsOverride.split(","))
          .map(String::trim)
          .filter(origin -> !origin.isEmpty())
          .forEach(origins::add);
    }
    corsConfig.setAllowedOrigins(new ArrayList<>(origins));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", corsConfig);
    return new CorsWebFilter(source);
  }
}
