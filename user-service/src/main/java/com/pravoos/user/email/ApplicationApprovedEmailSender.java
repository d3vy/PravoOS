package com.pravoos.user.email;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.event.ApplicationApprovedSpringEvent;
import com.pravoos.user.util.EmailMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ApplicationApprovedEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ApplicationApprovedEmailSender.class);

    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;

    public ApplicationApprovedEmailSender(ResendEmailClient resendEmailClient, ResendProperties resendProperties) {
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationApproved(ApplicationApprovedSpringEvent event) {
        String loginLink = resendProperties.frontendBaseUrl() + "/login";
        try {
            resendEmailClient.sendApprovalEmail(event.email(), event.fullName(), loginLink);
        } catch (Exception e) {
            log.error("Failed to send approval email to {}: {}", EmailMasker.mask(event.email()), e.getMessage());
        }
    }
}
