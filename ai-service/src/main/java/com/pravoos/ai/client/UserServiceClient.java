package com.pravoos.ai.client;

import com.pravoos.ai.config.UserServiceProperties;
import com.pravoos.ai.exception.OrgMembershipCheckException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String internalSecret;

    public UserServiceClient(UserServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.internalSecret = properties.internalSecret();
    }

    public boolean isOrgMember(UUID orgId, UUID userId) {
        try {
            OrgMembershipCheckResponse response = restClient.get()
                    .uri("/internal/org/{orgId}/members/{userId}", orgId, userId)
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .body(OrgMembershipCheckResponse.class);
            return response != null && response.member();
        } catch (RestClientException e) {
            log.error("Failed to verify org {} membership of user {} via user-service", orgId, userId, e);
            throw new OrgMembershipCheckException(orgId);
        }
    }

    private record OrgMembershipCheckResponse(boolean member) {}
}
