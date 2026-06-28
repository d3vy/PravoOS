package com.pravoos.ai.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration GUARD_READ_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CONNECTION_TIME_TO_LIVE = Duration.ofMinutes(5);
    private static final int MAX_TOTAL_CONNECTIONS = 50;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 50;

    @Bean
    public PoolingHttpClientConnectionManager openAiConnectionManager() {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                        .setTimeToLive(TimeValue.ofMilliseconds(CONNECTION_TIME_TO_LIVE.toMillis()))
                        .build())
                .setMaxConnTotal(MAX_TOTAL_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                .build();
    }

    @Bean
    public RestClient openAiRestClient(OpenAiProperties properties,
                                       PoolingHttpClientConnectionManager openAiConnectionManager) {
        return buildClient(properties, openAiConnectionManager, READ_TIMEOUT);
    }

    @Bean
    public RestClient openAiGuardRestClient(OpenAiProperties properties,
                                            PoolingHttpClientConnectionManager openAiConnectionManager) {
        return buildClient(properties, openAiConnectionManager, GUARD_READ_TIMEOUT);
    }

    private RestClient buildClient(OpenAiProperties properties,
                                   PoolingHttpClientConnectionManager connectionManager,
                                   Duration readTimeout) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory(connectionManager, readTimeout))
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    private ClientHttpRequestFactory requestFactory(PoolingHttpClientConnectionManager connectionManager,
                                                    Duration readTimeout) {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(readTimeout))
                        .build())
                .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }
}
