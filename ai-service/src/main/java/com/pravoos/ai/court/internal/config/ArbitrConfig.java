package com.pravoos.ai.court.internal.config;

import com.pravoos.ai.court.internal.CourtCaseProvider;
import com.pravoos.ai.court.internal.NoopCourtCaseProvider;
import com.pravoos.ai.court.internal.arbitr.ApiArbitrCaseProvider;
import com.pravoos.ai.shared.config.PooledClientHttpRequestFactories;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ArbitrProperties.class)
public class ArbitrConfig {

  private static final Logger log = LoggerFactory.getLogger(ArbitrConfig.class);
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
  private static final int MAX_TOTAL_CONNECTIONS = 10;

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
    ClientHttpRequestFactory requestFactory =
        PooledClientHttpRequestFactories.create(
            CONNECT_TIMEOUT, READ_TIMEOUT, MAX_TOTAL_CONNECTIONS, MAX_TOTAL_CONNECTIONS);

    return RestClient.builder()
        .baseUrl(properties.api().baseUrl())
        .requestFactory(requestFactory)
        .build();
  }
}
