package com.pravoos.cloud;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscoveryAwareRestClientsTest {

    private static final ClientHttpRequestInterceptor LOAD_BALANCING_MARKER =
            (request, body, execution) -> execution.execute(request, body);

    private RestClient.Builder loadBalancedBuilder() {
        return RestClient.builder().requestInterceptor(LOAD_BALANCING_MARKER);
    }

    @Test
    void serviceIdUrlKeepsLoadBalancingInterceptors() {
        RestClient.Builder builder = DiscoveryAwareRestClients.builderFor("lb://user-service", loadBalancedBuilder());

        assertThat(collectInterceptors(builder)).contains(LOAD_BALANCING_MARKER);
    }

    @Test
    void absoluteUrlBypassesLoadBalancing() {
        RestClient.Builder builder = DiscoveryAwareRestClients.builderFor("http://localhost:8081", loadBalancedBuilder());

        assertThat(collectInterceptors(builder)).doesNotContain(LOAD_BALANCING_MARKER);
    }

    @Test
    void serviceIdUrlDoesNotMutateSharedBuilder() {
        RestClient.Builder shared = loadBalancedBuilder();

        DiscoveryAwareRestClients.builderFor("lb://user-service", shared).baseUrl("lb://user-service");

        assertThat(DiscoveryAwareRestClients.isServiceId("lb://user-service")).isTrue();
        assertThat(collectInterceptors(shared)).containsExactly(LOAD_BALANCING_MARKER);
    }

    @Test
    void blankBaseUrlIsRejected() {
        assertThatThrownBy(() -> DiscoveryAwareRestClients.builderFor("  ", loadBalancedBuilder()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<ClientHttpRequestInterceptor> collectInterceptors(RestClient.Builder builder) {
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        builder.requestInterceptors(interceptors::addAll);
        return interceptors;
    }
}
