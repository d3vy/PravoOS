package com.pravoos.user.identity.internal.email;

import com.pravoos.user.shared.email.ResendEmailClient;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.identity.internal.event.PasswordResetRequestedEvent;
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
public class PasswordResetEmailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);
    private static final int MAX_EMAIL_ATTEMPTS = 3;

    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;
    private final Counter resetSentCounter;
    private final Counter resetFailedCounter;

    public PasswordResetEmailSender(ResendEmailClient resendEmailClient,
                                    ResendProperties resendProperties,
                                    MeterRegistry meterRegistry) {
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
        this.resetSentCounter = Counter.builder("pravoos.email.password_reset").tag("result", "sent").register(meterRegistry);
        this.resetFailedCounter = Counter.builder("pravoos.email.password_reset").tag("result", "failed").register(meterRegistry);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        String resetLink = resendProperties.frontendBaseUrl() + "/reset-password?token=" + event.rawToken();
        for (int attempt = 1; attempt <= MAX_EMAIL_ATTEMPTS; attempt++) {
            try {
                resendEmailClient.sendPasswordResetEmail(event.email(), resetLink);
                resetSentCounter.increment();
                return;
            } catch (Exception e) {
                if (attempt == MAX_EMAIL_ATTEMPTS) {
                    resetFailedCounter.increment();
                    log.error("Failed to send password reset email to {} after {} attempts: {}",
                            EmailMasker.mask(event.email()), MAX_EMAIL_ATTEMPTS, e.getMessage());
                    return;
                }
                log.warn("Password reset email attempt {}/{} failed for {}: {}",
                        attempt, MAX_EMAIL_ATTEMPTS, EmailMasker.mask(event.email()), e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    resetFailedCounter.increment();
                    return;
                }
            }
        }
    }
}
