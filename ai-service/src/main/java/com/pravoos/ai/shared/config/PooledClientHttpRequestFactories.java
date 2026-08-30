package com.pravoos.ai.shared.config;

import java.time.Duration;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

public final class PooledClientHttpRequestFactories {

  private static final Duration CONNECTION_TIME_TO_LIVE = Duration.ofMinutes(5);

  private PooledClientHttpRequestFactories() {}

  public static ClientHttpRequestFactory create(
      Duration connectTimeout, Duration readTimeout, int maxTotal, int maxPerRoute) {
    PoolingHttpClientConnectionManager connectionManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.of(connectTimeout))
                    .setTimeToLive(TimeValue.ofMilliseconds(CONNECTION_TIME_TO_LIVE.toMillis()))
                    .build())
            .setMaxConnTotal(maxTotal)
            .setMaxConnPerRoute(maxPerRoute)
            .build();
    CloseableHttpClient httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setConnectionManagerShared(false)
            .setDefaultRequestConfig(
                RequestConfig.custom().setResponseTimeout(Timeout.of(readTimeout)).build())
            .build();
    return new HttpComponentsClientHttpRequestFactory(httpClient);
  }
}
