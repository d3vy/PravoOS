package com.pravoos.user.email;

import com.pravoos.user.config.ResendProperties;
import com.pravoos.user.event.ApplicationSubmittedSpringEvent;
import com.pravoos.user.event.VerificationEmailRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailVerificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationSender.class);

    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;

    public EmailVerificationSender(ResendEmailClient resendEmailClient, ResendProperties resendProperties) {
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationSubmitted(ApplicationSubmittedSpringEvent event) {
        String email = event.application().getEmail();
        String token = event.application().getEmailVerificationToken();

        if (token == null) {
            log.warn("No verification token on application for {}, skipping email", email);
            return;
        }

        sendVerificationEmail(email, token);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequestedEvent event) {
        sendVerificationEmail(event.email(), event.rawToken());
    }

    private void sendVerificationEmail(String email, String token) {
        String verificationLink = resendProperties.frontendBaseUrl() + "/verify-email?token=" + token;
        try {
            resendEmailClient.sendVerificationEmail(email, verificationLink);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage());
        }
    }
}
