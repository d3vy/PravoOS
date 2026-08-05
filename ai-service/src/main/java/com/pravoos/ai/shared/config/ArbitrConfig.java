package com.pravoos.ai.shared.config;

import com.pravoos.ai.shared.court.CourtCaseProvider;
import com.pravoos.ai.shared.court.NoopCourtCaseProvider;
import com.pravoos.ai.shared.court.arbitr.ApiArbitrCaseProvider;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ArbitrConfig {

  private static final Logger log = LoggerFactory.getLogger(ArbitrConfig.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

  @Bean
  public CourtCaseProvider arbitrCaseProvider(ArbitrProperties properties) {
    if (!properties.api().hasKey()) {
      log.warn("ARBITR_API_KEY не задан — интеграция с КАД.Арбитр отключена (Noop-провайдер)");
      return new NoopCourtCaseProvider(CourtSystem.ARBITR);
    }
    log.info("Интеграция с КАД.Арбитр включена, baseUrl={}", properties.api().baseUrl());
    return new ApiArbitrCaseProvider(buildRestClient(properties), properties);
  }

  private RestClient buildRestClient(ArbitrProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);

    return RestClient.builder()
        .baseUrl(properties.api().baseUrl())
        .requestFactory(requestFactory)
        .build();
  }
}
