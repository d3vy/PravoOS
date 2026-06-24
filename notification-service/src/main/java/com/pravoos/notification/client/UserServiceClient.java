package com.pravoos.notification.client;

import com.pravoos.notification.config.UserServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
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

    public void approveApplication(UUID applicationId) {
        restClient.post()
                .uri("/internal/applications/{id}/approve", applicationId)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .toBodilessEntity();
        log.info("Application approved via internal API: {}", applicationId);
    }

    public void approveApplicationForce(UUID applicationId) {
        restClient.post()
                .uri("/internal/applications/{id}/approve-force", applicationId)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .toBodilessEntity();
        log.info("Application force-approved via internal API: {}", applicationId);
    }

    public void rejectApplication(UUID applicationId) {
        restClient.post()
                .uri("/internal/applications/{id}/reject", applicationId)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .toBodilessEntity();
        log.info("Application rejected via internal API: {}", applicationId);
    }

    public String bindTelegram(String code, long chatId) {
        BindTelegramResponse response = restClient.post()
                .uri("/internal/telegram/bind")
                .header("X-Internal-Secret", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new BindTelegramRequest(code, chatId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, clientResponse) -> {
                    throw new TelegramBindException(
                            "Код недействителен или истёк. Сгенерируйте новую ссылку привязки в личном кабинете PravoOS.");
                })
                .body(BindTelegramResponse.class);
        log.info("Telegram bound for chat {}", chatId);
        return response != null ? response.fullName() : null;
    }

    public void sendDeadlineEmail(DeadlineEmailRequest request) {
        restClient.post()
                .uri("/internal/notifications/deadline-email")
                .header("X-Internal-Secret", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        log.info("Deadline email requested for lawyer {} case {}", request.lawyerId(), request.caseId());
    }

    public Optional<Long> resolveTelegramChatId(UUID lawyerId) {
        try {
            TelegramChatIdResponse response = restClient.get()
                    .uri("/internal/telegram/chat-id/{lawyerId}", lawyerId)
                    .header("X-Internal-Secret", internalSecret)
                    .retrieve()
                    .body(TelegramChatIdResponse.class);
            return response == null ? Optional.empty() : Optional.ofNullable(response.chatId());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
