package com.pravoos.user.collaboration.internal.service;

import com.pravoos.user.collaboration.internal.dto.DeadlineEmailRequest;
import com.pravoos.user.identity.model.entity.User;
import com.pravoos.user.identity.model.enums.UserRole;
import com.pravoos.user.identity.model.enums.UserStatus;
import com.pravoos.user.identity.repository.UserRepository;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.email.ResendEmailClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DeadlineNotificationEmailService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineNotificationEmailService.class);

    private final UserRepository userRepository;
    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;

    public DeadlineNotificationEmailService(UserRepository userRepository,
                                            ResendEmailClient resendEmailClient,
                                            ResendProperties resendProperties) {
        this.userRepository = userRepository;
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
    }

    public void sendDeadlineEmail(DeadlineEmailRequest request) {
        String recipientEmail = resolveActiveLawyerEmail(request.lawyerId());
        if (recipientEmail == null) {
            log.warn("Skipping deadline email: lawyer {} not found or not active", request.lawyerId());
            return;
        }

        String caseLink = resendProperties.frontendBaseUrl() + "/cases/" + request.caseId();
        try {
            resendEmailClient.sendDeadlineEmail(recipientEmail, request.caseTitle(),
                    request.deadlineTypeName(), request.deadlineDate(), request.daysLeft(), caseLink);
        } catch (Exception e) {
            log.error("Failed to send deadline email to lawyer {}: {}", request.lawyerId(), e.getMessage(), e);
        }
    }

    private String resolveActiveLawyerEmail(UUID lawyerId) {
        User user = userRepository.findById(lawyerId).orElse(null);
        if (user == null || user.getRole() != UserRole.LAWYER || user.getStatus() != UserStatus.ACTIVE) {
            return null;
        }
        return user.getEmail();
    }
}
