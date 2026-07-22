package com.pravoos.cloud;

import org.springframework.web.client.RestClient;

public final class DiscoveryAwareRestClients {

    private static final String LOAD_BALANCED_SCHEME = "lb://";

    private DiscoveryAwareRestClients() {
    }

    public static boolean isServiceId(String baseUrl) {
        return baseUrl != null && baseUrl.startsWith(LOAD_BALANCED_SCHEME);
    }

    public static RestClient.Builder builderFor(String baseUrl, RestClient.Builder loadBalancedBuilder) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        return isServiceId(baseUrl) ? loadBalancedBuilder.clone() : RestClient.builder();
    }
}
