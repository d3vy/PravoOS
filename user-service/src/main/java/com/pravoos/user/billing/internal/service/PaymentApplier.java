package com.pravoos.user.billing.internal.service;

import com.pravoos.user.billing.internal.client.YooKassaPayment;
import com.pravoos.user.billing.internal.config.YooKassaProperties;
import com.pravoos.user.billing.internal.model.entity.Payment;
import com.pravoos.user.billing.internal.model.enums.PaymentStatus;
import com.pravoos.user.billing.internal.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class PaymentApplier {

    private static final Logger log = LoggerFactory.getLogger(PaymentApplier.class);

    private final PaymentRepository paymentRepository;
    private final SubscriptionService subscriptionService;
    private final YooKassaProperties properties;

    public PaymentApplier(PaymentRepository paymentRepository,
                          SubscriptionService subscriptionService,
                          YooKassaProperties properties) {
        this.paymentRepository = paymentRepository;
        this.subscriptionService = subscriptionService;
        this.properties = properties;
    }

    @Transactional
    public void apply(YooKassaPayment snapshot) {
        Payment payment = paymentRepository.findByProviderPaymentId(snapshot.id()).orElse(null);
        if (payment == null) {
            log.warn("Webhook for unknown payment {} ignored", snapshot.id());
            return;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Webhook for already processed payment {} ignored ({})", snapshot.id(), payment.getStatus());
            return;
        }

        if (snapshot.canceled()) {
            payment.setStatus(PaymentStatus.CANCELED);
            paymentRepository.save(payment);
            log.info("Payment {} canceled for user {}", snapshot.id(), payment.getUserId());
            return;
        }
        if (!snapshot.succeeded()) {
            log.info("Payment {} still in status '{}' — nothing to apply", snapshot.id(), snapshot.status());
            return;
        }
        if (snapshot.amountKopecks() != payment.getAmountKopecks()) {
            log.error("Payment {} amount mismatch: provider {} kopecks, expected {} — not activating",
                    snapshot.id(), snapshot.amountKopecks(), payment.getAmountKopecks());
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
        paymentRepository.save(payment);
        subscriptionService.activateOnPlan(payment.getUserId(), payment.getPlanId(), properties.periodDays());

        log.info("Payment {} succeeded for user {}", snapshot.id(), payment.getUserId());
    }
}
