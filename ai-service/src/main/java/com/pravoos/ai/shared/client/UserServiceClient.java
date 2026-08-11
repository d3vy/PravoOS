package com.pravoos.ai.shared.client;

import com.pravoos.ai.shared.config.UserServiceProperties;
import com.pravoos.ai.shared.dto.PortalInviteStatusResponse;
import com.pravoos.ai.shared.exception.DigestPreferenceCheckException;
import com.pravoos.ai.shared.exception.OrgMembershipCheckException;
import com.pravoos.ai.shared.exception.PortalInviteException;
import com.pravoos.cloud.DiscoveryAwareRestClients;
import com.pravoos.common.security.internal.InternalCallerHeaders;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserServiceClient {

  private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
  private static final String CALLER_NAME = "ai-service";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

  private final RestClient restClient;

  public UserServiceClient(
      UserServiceProperties properties,
      @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient =
        DiscoveryAwareRestClients.builderFor(properties.baseUrl(), loadBalancedRestClientBuilder)
            .baseUrl(properties.baseUrl())
            .defaultHeader(InternalCallerHeaders.CALLER, CALLER_NAME)
            .defaultHeader(InternalCallerHeaders.SECRET, properties.internalSecret())
            .requestFactory(requestFactory)
            .build();
  }

  public boolean isOrgMember(UUID orgId, UUID userId) {
    try {
      OrgMembershipCheckResponse response =
          restClient
              .get()
              .uri("/internal/org/{orgId}/members/{userId}", orgId, userId)
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
      restClient
          .post()
          .uri("/internal/portal-invites")
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
      return restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/internal/portal-invites/status")
                      .queryParam("clientId", clientId)
                      .build())
          .retrieve()
          .body(PortalInviteStatusResponse.class);
    } catch (RestClientException e) {
      log.error("Failed to fetch portal invite status for client {} via user-service", clientId, e);
      throw new PortalInviteException(clientId);
    }
  }

  public void revokePortalInvite(UUID clientId) {
    try {
      restClient
          .delete()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/internal/portal-invites")
                      .queryParam("clientId", clientId)
                      .build())
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.error("Failed to revoke portal invite for client {} via user-service", clientId, e);
      throw new PortalInviteException(clientId);
    }
  }

  public List<UUID> filterDigestEnabledLawyerIds(List<UUID> lawyerIds) {
    try {
      DigestPreferenceResponse response =
          restClient
              .post()
              .uri("/internal/users/digest-preferences")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new DigestPreferenceRequest(lawyerIds))
              .retrieve()
              .body(DigestPreferenceResponse.class);
      return response == null ? List.of() : response.userIds();
    } catch (RestClientException e) {
      log.error("Failed to fetch digest preferences via user-service", e);
      throw new DigestPreferenceCheckException();
    }
  }

  private record OrgMembershipCheckResponse(boolean member) {}

  private record CreatePortalInviteRequest(
      UUID clientId, UUID lawyerId, String email, String clientName) {}

  private record DigestPreferenceRequest(List<UUID> userIds) {}

  private record DigestPreferenceResponse(List<UUID> userIds) {}
}
