package com.pravoos.user.identity.internal.email;

import com.pravoos.user.shared.email.ResendEmailClient;
import com.pravoos.user.identity.internal.event.NewLoginEvent;
import com.pravoos.user.shared.util.EmailMasker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

@Component
public class NewLoginEmailSender {

    private static final Logger log = LoggerFactory.getLogger(NewLoginEmailSender.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ResendEmailClient resendEmailClient;
    private final Counter sentCounter;
    private final Counter failedCounter;

    public NewLoginEmailSender(ResendEmailClient resendEmailClient, MeterRegistry meterRegistry) {
        this.resendEmailClient = resendEmailClient;
        this.sentCounter = Counter.builder("pravoos.email.new_login").tag("result", "sent").register(meterRegistry);
        this.failedCounter = Counter.builder("pravoos.email.new_login").tag("result", "failed").register(meterRegistry);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewLogin(NewLoginEvent event) {
        try {
            resendEmailClient.sendNewLoginEmail(
                    event.email(), event.ipAddress(), event.userAgent(), event.occurredAt().format(FORMATTER));
            sentCounter.increment();
        } catch (Exception e) {
            failedCounter.increment();
            log.error("Failed to send new-login email to {}: {}", EmailMasker.mask(event.email()), e.getMessage());
        }
    }
}
