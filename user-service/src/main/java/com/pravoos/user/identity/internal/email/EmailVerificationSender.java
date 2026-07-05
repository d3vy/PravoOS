package com.pravoos.user.identity.internal.email;

import com.pravoos.user.shared.email.ResendEmailClient;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.event.ApplicationSubmittedSpringEvent;
import com.pravoos.user.identity.internal.event.VerificationEmailRequestedEvent;
import com.pravoos.user.shared.util.EmailMasker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EmailVerificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationSender.class);
    private static final int MAX_EMAIL_ATTEMPTS = 3;

    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;
    private final Counter verificationSentCounter;
    private final Counter verificationFailedCounter;

    public EmailVerificationSender(ResendEmailClient resendEmailClient,
                                   ResendProperties resendProperties,
                                   MeterRegistry meterRegistry) {
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
        this.verificationSentCounter = Counter.builder("pravoos.email.verification").tag("result", "sent").register(meterRegistry);
        this.verificationFailedCounter = Counter.builder("pravoos.email.verification").tag("result", "failed").register(meterRegistry);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationSubmitted(ApplicationSubmittedSpringEvent event) {
        if (event.rawVerificationToken() == null) {
            log.warn("No verification token for {}, skipping email", EmailMasker.mask(event.email()));
            return;
        }
        sendVerificationEmail(event.email(), event.rawVerificationToken());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequestedEvent event) {
        sendVerificationEmail(event.email(), event.rawToken());
    }

    private void sendVerificationEmail(String email, String token) {
        String verificationLink = resendProperties.frontendBaseUrl() + "/verify-email?token=" + token;
        for (int attempt = 1; attempt <= MAX_EMAIL_ATTEMPTS; attempt++) {
            try {
                resendEmailClient.sendVerificationEmail(email, verificationLink);
                verificationSentCounter.increment();
                return;
            } catch (Exception e) {
                if (attempt == MAX_EMAIL_ATTEMPTS) {
                    verificationFailedCounter.increment();
                    log.error("Failed to send verification email to {} after {} attempts: {}",
                            EmailMasker.mask(email), MAX_EMAIL_ATTEMPTS, e.getMessage());
                    return;
                }
                log.warn("Verification email attempt {}/{} failed for {}: {}",
                        attempt, MAX_EMAIL_ATTEMPTS, EmailMasker.mask(email), e.getMessage());
                if (!backoff(attempt)) {
                    verificationFailedCounter.increment();
                    return;
                }
            }
        }
    }

    private boolean backoff(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
