package com.pravoos.user.service;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.email.ResendEmailClient;
import com.pravoos.user.model.dto.CaseMessageNotificationRequest;
import com.pravoos.user.model.dto.CaseMessageNotificationResult;
import com.pravoos.user.model.entity.User;
import com.pravoos.user.model.enums.UserStatus;
import com.pravoos.user.repository.ClientPortalInviteRepository;
import com.pravoos.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CaseMessageNotificationService {

    private static final Logger log = LoggerFactory.getLogger(CaseMessageNotificationService.class);
    private static final String LAWYER_SENDER_LABEL = "Ваш юрист";
    private static final String CLIENT_SENDER_LABEL = "Клиент";

    private final UserRepository userRepository;
    private final ClientPortalInviteRepository clientPortalInviteRepository;
    private final TelegramLinkService telegramLinkService;
    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;

    public CaseMessageNotificationService(UserRepository userRepository,
                                          ClientPortalInviteRepository clientPortalInviteRepository,
                                          TelegramLinkService telegramLinkService,
                                          ResendEmailClient resendEmailClient,
                                          ResendProperties resendProperties) {
        this.userRepository = userRepository;
        this.clientPortalInviteRepository = clientPortalInviteRepository;
        this.telegramLinkService = telegramLinkService;
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
    }

    public CaseMessageNotificationResult dispatch(CaseMessageNotificationRequest request) {
        boolean recipientIsLawyer = request.recipientLawyerId() != null;
        User recipient = resolveRecipient(request);
        if (recipient == null) {
            log.info("No active recipient for case {} message notification", request.caseId());
            return CaseMessageNotificationResult.none();
        }

        String caseLink = buildCaseLink(request.caseId(), recipientIsLawyer);
        if (recipient.isCaseMessageEmail()) {
            sendEmail(recipient, request, caseLink);
        }
        if (recipient.isCaseMessageTelegram()) {
            return new CaseMessageNotificationResult(
                    telegramLinkService.resolveChatId(recipient.getId()).orElse(null));
        }
        return CaseMessageNotificationResult.none();
    }

    private User resolveRecipient(CaseMessageNotificationRequest request) {
        if (request.recipientLawyerId() != null) {
            return activeUser(request.recipientLawyerId());
        }
        if (request.recipientClientId() != null) {
            List<UUID> userIds = clientPortalInviteRepository
                    .findAcceptedUserIdsByClientId(request.recipientClientId());
            return userIds.stream()
                    .map(this::activeUser)
                    .filter(user -> user != null)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private User activeUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
    }

    private void sendEmail(User recipient, CaseMessageNotificationRequest request, String caseLink) {
        try {
            resendEmailClient.sendCaseMessageEmail(recipient.getEmail(), request.caseTitle(),
                    senderLabel(request.authorRole()), preview(request.preview()), caseLink);
        } catch (Exception e) {
            log.error("Failed to send case message email for case {}: {}", request.caseId(), e.getMessage(), e);
        }
    }

    private String buildCaseLink(UUID caseId, boolean recipientIsLawyer) {
        String path = recipientIsLawyer ? "/cases/" : "/portal/cases/";
        return resendProperties.frontendBaseUrl() + path + caseId;
    }

    private String senderLabel(String authorRole) {
        return "CLIENT".equals(authorRole) ? CLIENT_SENDER_LABEL : LAWYER_SENDER_LABEL;
    }

    private String preview(String preview) {
        return preview == null || preview.isBlank() ? "Новое сообщение" : preview;
    }
}
