package com.pravoos.notification.client;

import com.pravoos.notification.config.UserServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestClient restClient;
    private final String internalSecret;

    public UserServiceClient(UserServiceProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
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

    public void rejectApplication(UUID applicationId) {
        restClient.post()
                .uri("/internal/applications/{id}/reject", applicationId)
                .header("X-Internal-Secret", internalSecret)
                .retrieve()
                .toBodilessEntity();
        log.info("Application rejected via internal API: {}", applicationId);
    }
}
