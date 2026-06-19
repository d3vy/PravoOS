package com.pravoos.user.service;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.email.ResendEmailClient;
import com.pravoos.user.model.dto.DeadlineEmailRequest;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserRole;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public void sendDeadlineEmail(DeadlineEmailRequest request) {
        User user = userRepository.findById(request.lawyerId()).orElse(null);
        if (user == null || user.getRole() != UserRole.LAWYER || user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Skipping deadline email: lawyer {} not found or not active", request.lawyerId());
            return;
        }

        String caseLink = resendProperties.frontendBaseUrl() + "/cases/" + request.caseId();
        try {
            resendEmailClient.sendDeadlineEmail(user.getEmail(), request.caseTitle(),
                    request.deadlineTypeName(), request.deadlineDate(), request.daysLeft(), caseLink);
        } catch (Exception e) {
            log.error("Failed to send deadline email to lawyer {}: {}", request.lawyerId(), e.getMessage(), e);
        }
    }
}
