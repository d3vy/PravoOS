package com.pravoos.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.billing.internal.client.YooKassaPayment;
import com.pravoos.user.billing.internal.config.YooKassaProperties;
import com.pravoos.user.billing.internal.model.entity.Payment;
import com.pravoos.user.billing.internal.model.enums.PaymentStatus;
import com.pravoos.user.billing.internal.repository.PaymentRepository;
import com.pravoos.user.billing.internal.service.PaymentApplier;
import com.pravoos.user.billing.internal.service.SubscriptionService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentApplierTest {

  private static final String PROVIDER_PAYMENT_ID = "2c8d1f5e-000f-5000-9000-1b68e7b15d1a";
  private static final long AMOUNT_KOPECKS = 149000L;
  private static final int PERIOD_DAYS = 30;

  @Mock private PaymentRepository paymentRepository;

  @Mock private SubscriptionService subscriptionService;

  private PaymentApplier paymentApplier;
  private UUID userId;
  private UUID planId;

  @BeforeEach
  void setup() {
    YooKassaProperties properties =
        new YooKassaProperties(
            "shop", "secret", "http://localhost/billing", PERIOD_DAYS, List.of());
    paymentApplier = new PaymentApplier(paymentRepository, subscriptionService, properties);
    userId = UUID.randomUUID();
    planId = UUID.randomUUID();
  }

  @Test
  void succeededPaymentActivatesSubscription() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID))
        .thenReturn(Optional.of(payment));

    paymentApplier.apply(succeeded(AMOUNT_KOPECKS));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(payment.getPaidAt()).isNotNull();
    verify(paymentRepository).save(payment);
    verify(subscriptionService).activateOnPlan(userId, planId, PERIOD_DAYS);
  }

  @Test
  void alreadyProcessedPaymentIsIgnored() {
    Payment payment = pendingPayment();
    payment.setStatus(PaymentStatus.SUCCEEDED);
    when(paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID))
        .thenReturn(Optional.of(payment));

    paymentApplier.apply(succeeded(AMOUNT_KOPECKS));

    verify(paymentRepository, never()).save(any());
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void amountMismatchDoesNotActivateSubscription() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID))
        .thenReturn(Optional.of(payment));

    paymentApplier.apply(succeeded(1000L));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(paymentRepository, never()).save(any());
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void canceledPaymentIsMarkedCanceled() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID))
        .thenReturn(Optional.of(payment));

    paymentApplier.apply(
        new YooKassaPayment(PROVIDER_PAYMENT_ID, "canceled", false, AMOUNT_KOPECKS, null));

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void unknownPaymentIsIgnored() {
    when(paymentRepository.findByProviderPaymentId(PROVIDER_PAYMENT_ID))
        .thenReturn(Optional.empty());

    paymentApplier.apply(succeeded(AMOUNT_KOPECKS));

    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  private YooKassaPayment succeeded(long amountKopecks) {
    return new YooKassaPayment(PROVIDER_PAYMENT_ID, "succeeded", true, amountKopecks, null);
  }

  private Payment pendingPayment() {
    Payment payment = new Payment();
    payment.setUserId(userId);
    payment.setPlanId(planId);
    payment.setProviderPaymentId(PROVIDER_PAYMENT_ID);
    payment.setAmountKopecks(AMOUNT_KOPECKS);
    payment.setStatus(PaymentStatus.PENDING);
    return payment;
  }
}
