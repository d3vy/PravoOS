package com.pravoos.ai.shared.client;

import com.pravoos.ai.shared.config.UserServiceProperties;
import com.pravoos.ai.shared.dto.PortalInviteStatusResponse;
import com.pravoos.ai.shared.exception.OrgMembershipCheckException;
import com.pravoos.ai.shared.exception.PortalInviteException;
import com.pravoos.cloud.DiscoveryAwareRestClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
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

    public UserServiceClient(UserServiceProperties properties,
                            @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = DiscoveryAwareRestClients.builderFor(properties.baseUrl(), loadBalancedRestClientBuilder)
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

    public void createPortalInvite(UUID clientId, UUID lawyerId, String email, String clientName) {
        try {
            restClient.post()
                    .uri("/internal/portal-invites")
                    .header("X-Internal-Secret", internalSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CreatePortalInviteRequest(clientId, lawyerId, email, clientName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to create portal invite for client {} via user-service", clientId, e);
            throw new PortalInviteException(clientId);
        }
    }

    public PortalInviteStatusResponse getPortalInviteStatus(UUID clientId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/portal-invites/status")
                            .queryParam("clientId", clientId)
                            .build())
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .body(PortalInviteStatusResponse.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch portal invite status for client {} via user-service", clientId, e);
            throw new PortalInviteException(clientId);
        }
    }

    public void revokePortalInvite(UUID clientId) {
        try {
            restClient.delete()
                    .uri(uriBuilder -> uriBuilder.path("/internal/portal-invites")
                            .queryParam("clientId", clientId)
                            .build())
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to revoke portal invite for client {} via user-service", clientId, e);
            throw new PortalInviteException(clientId);
        }
    }

    private record OrgMembershipCheckResponse(boolean member) {}

    private record CreatePortalInviteRequest(UUID clientId, UUID lawyerId, String email, String clientName) {}
}
