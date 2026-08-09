package com.pravoos.notification.client;

import com.pravoos.cloud.DiscoveryAwareRestClients;
import com.pravoos.notification.config.UserServiceProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class UserServiceClient {

  private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);
  private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

  private final RestClient restClient;

  @Autowired
  public UserServiceClient(
      UserServiceProperties properties,
      @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder) {
    this(restClientFor(properties, loadBalancedRestClientBuilder));
  }

  UserServiceClient(RestClient restClient) {
    this.restClient = restClient;
  }

  private static RestClient restClientFor(
      UserServiceProperties properties, RestClient.Builder loadBalancedRestClientBuilder) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return DiscoveryAwareRestClients.builderFor(properties.baseUrl(), loadBalancedRestClientBuilder)
        .baseUrl(properties.baseUrl())
        .defaultHeader(INTERNAL_SECRET_HEADER, properties.internalSecret())
        .requestFactory(requestFactory)
        .build();
  }

  public void approveApplication(UUID applicationId) {
    restClient
        .post()
        .uri("/internal/applications/{id}/approve", applicationId)
        .retrieve()
        .toBodilessEntity();
    log.info("Application approved via internal API: {}", applicationId);
  }

  public void approveApplicationForce(UUID applicationId) {
    restClient
        .post()
        .uri("/internal/applications/{id}/approve-force", applicationId)
        .retrieve()
        .toBodilessEntity();
    log.info("Application force-approved via internal API: {}", applicationId);
  }

  public void rejectApplication(UUID applicationId) {
    restClient
        .post()
        .uri("/internal/applications/{id}/reject", applicationId)
        .retrieve()
        .toBodilessEntity();
    log.info("Application rejected via internal API: {}", applicationId);
  }

  public Optional<ApplicationDetailsResponse> getApplication(UUID applicationId) {
    try {
      ApplicationDetailsResponse details =
          restClient
              .get()
              .uri("/internal/applications/{id}", applicationId)
              .retrieve()
              .body(ApplicationDetailsResponse.class);
      return Optional.ofNullable(details);
    } catch (HttpClientErrorException.NotFound e) {
      log.warn("Application {} not found in user-service", applicationId);
      return Optional.empty();
    }
  }

  public String bindTelegram(String code, long chatId) {
    BindTelegramResponse response =
        restClient
            .post()
            .uri("/internal/telegram/bind")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new BindTelegramRequest(code, chatId))
            .retrieve()
            .onStatus(
                HttpStatusCode::is4xxClientError,
                (request, clientResponse) -> {
                  throw new TelegramBindException(
                      "Код недействителен или истёк. Сгенерируйте новую ссылку привязки в личном кабинете PravoOS.");
                })
            .body(BindTelegramResponse.class);
    log.info("Telegram bound for chat {}", chatId);
    return response != null ? response.fullName() : null;
  }

  public void sendDeadlineEmail(DeadlineEmailRequest request) {
    restClient
        .post()
        .uri("/internal/notifications/deadline-email")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .toBodilessEntity();
    log.info(
        "Deadline email requested for lawyer {} case {}", request.lawyerId(), request.caseId());
  }

  public CaseMessageNotificationResult dispatchCaseMessage(CaseMessageNotificationRequest request) {
    CaseMessageNotificationResult result =
        restClient
            .post()
            .uri("/internal/notifications/case-message")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(CaseMessageNotificationResult.class);
    log.info("Case message notification dispatched for case {}", request.caseId());
    return result == null ? CaseMessageNotificationResult.none() : result;
  }

  public List<PushSubscriptionResponse> listPushSubscriptions(UUID userId) {
    List<PushSubscriptionResponse> subscriptions =
        restClient
            .get()
            .uri("/internal/push/subscriptions/{userId}", userId)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    return subscriptions == null ? List.of() : subscriptions;
  }

  public void prunePushSubscription(String endpoint) {
    restClient
        .post()
        .uri("/internal/push/subscriptions/prune")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new PrunePushSubscriptionRequest(endpoint))
        .retrieve()
        .toBodilessEntity();
    log.info("Expired push subscription pruned in user-service");
  }

  public Optional<Long> resolveTelegramChatId(UUID lawyerId) {
    try {
      TelegramChatIdResponse response =
          restClient
              .get()
              .uri("/internal/telegram/chat-id/{lawyerId}", lawyerId)
              .retrieve()
              .body(TelegramChatIdResponse.class);
      return response == null ? Optional.empty() : Optional.ofNullable(response.chatId());
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    }
  }
}
