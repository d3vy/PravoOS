package com.pravoos.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CloudClientAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LoadBalancerAutoConfiguration.class,
                    CloudClientAutoConfiguration.class))
            .withUserConfiguration(LoadBalancerStubConfiguration.class);

    @Test
    void loadBalancedRestClientBuilderIsInstrumented() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("loadBalancedRestClientBuilder");
            RestClient.Builder builder = context.getBean("loadBalancedRestClientBuilder", RestClient.Builder.class);

            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
            builder.requestInterceptors(interceptors::addAll);
            assertThat(interceptors).isNotEmpty();
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class LoadBalancerStubConfiguration {

        @Bean
        LoadBalancerClient loadBalancerClient() {
            return mock(LoadBalancerClient.class);
        }
    }
}
