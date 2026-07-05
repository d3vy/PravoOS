package com.pravoos.user.registration.internal.email;

import com.pravoos.user.shared.email.ResendEmailClient;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.registration.internal.event.ApplicationApprovedSpringEvent;
import com.pravoos.user.shared.util.EmailMasker;
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

    private static final int MAX_EMAIL_ATTEMPTS = 3;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationApproved(ApplicationApprovedSpringEvent event) {
        String loginLink = resendProperties.frontendBaseUrl() + "/login";
        for (int attempt = 1; attempt <= MAX_EMAIL_ATTEMPTS; attempt++) {
            try {
                resendEmailClient.sendApprovalEmail(event.email(), event.fullName(), loginLink);
                return;
            } catch (Exception e) {
                if (attempt == MAX_EMAIL_ATTEMPTS) {
                    log.error("Failed to send approval email to {} after {} attempts: {}",
                            EmailMasker.mask(event.email()), MAX_EMAIL_ATTEMPTS, e.getMessage());
                    return;
                }
                log.warn("Approval email attempt {}/{} failed for {}: {}",
                        attempt, MAX_EMAIL_ATTEMPTS, EmailMasker.mask(event.email()), e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
