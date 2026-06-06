package com.pravoos.user.email;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.event.PasswordResetRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);

    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;

    public PasswordResetEmailSender(ResendEmailClient resendEmailClient, ResendProperties resendProperties) {
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        String resetLink = resendProperties.frontendBaseUrl() + "/reset-password?token=" + event.rawToken();

        try {
            resendEmailClient.sendPasswordResetEmail(event.email(), resetLink);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", event.email(), e.getMessage());
        }
    }
}
