package com.pravoos.user.collaboration.internal.email;

import com.pravoos.user.collaboration.internal.event.ClientPortalInviteCreatedEvent;
import com.pravoos.user.shared.config.ResendProperties;
import com.pravoos.user.shared.email.ResendEmailClient;
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
public class ClientPortalInviteEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ClientPortalInviteEmailSender.class);
    private static final int MAX_EMAIL_ATTEMPTS = 3;

    private final ResendEmailClient resendEmailClient;
    private final ResendProperties resendProperties;
    private final Counter sentCounter;
    private final Counter failedCounter;

    public ClientPortalInviteEmailSender(ResendEmailClient resendEmailClient,
                                         ResendProperties resendProperties,
                                         MeterRegistry meterRegistry) {
        this.resendEmailClient = resendEmailClient;
        this.resendProperties = resendProperties;
        this.sentCounter = Counter.builder("pravoos.email.portal_invite").tag("result", "sent").register(meterRegistry);
        this.failedCounter = Counter.builder("pravoos.email.portal_invite").tag("result", "failed").register(meterRegistry);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteCreated(ClientPortalInviteCreatedEvent event) {
        String acceptLink = resendProperties.frontendBaseUrl() + "/portal/accept?token=" + event.rawToken();
        for (int attempt = 1; attempt <= MAX_EMAIL_ATTEMPTS; attempt++) {
            try {
                resendEmailClient.sendClientPortalInviteEmail(event.email(), event.clientName(), acceptLink);
                sentCounter.increment();
                return;
            } catch (Exception e) {
                if (attempt == MAX_EMAIL_ATTEMPTS) {
                    failedCounter.increment();
                    log.error("Failed to send portal invite email to {} after {} attempts: {}",
                            EmailMasker.mask(event.email()), MAX_EMAIL_ATTEMPTS, e.getMessage());
                    return;
                }
                log.warn("Portal invite email attempt {}/{} failed for {}: {}",
                        attempt, MAX_EMAIL_ATTEMPTS, EmailMasker.mask(event.email()), e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failedCounter.increment();
                    return;
                }
            }
        }
    }
}
