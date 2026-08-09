package com.pravoos.user.billing.internal.service;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentApplierTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private SubscriptionService subscriptionService;

  private YooKassaProperties properties;
  private PaymentApplier applier;

  @BeforeEach
  void setUp() {
    properties = new YooKassaProperties("shop", "secret", "https://return.url", 30, List.of());
    applier = new PaymentApplier(paymentRepository, subscriptionService, properties);
  }

  @Test
  void apply_ignoresWebhookForUnknownPayment() {
    when(paymentRepository.findByProviderPaymentIdForUpdate("yk-1")).thenReturn(Optional.empty());

    applier.apply(new YooKassaPayment("yk-1", "succeeded", true, 1000, null));

    verify(paymentRepository, never()).save(any());
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void apply_ignoresWebhookForAlreadyProcessedPayment() {
    Payment payment = payment(PaymentStatus.SUCCEEDED, 1000);
    when(paymentRepository.findByProviderPaymentIdForUpdate("yk-1"))
        .thenReturn(Optional.of(payment));

    applier.apply(new YooKassaPayment("yk-1", "succeeded", true, 1000, null));

    verify(paymentRepository, never()).save(any());
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void apply_marksPaymentCanceled() {
    Payment payment = payment(PaymentStatus.PENDING, 1000);
    when(paymentRepository.findByProviderPaymentIdForUpdate("yk-1"))
        .thenReturn(Optional.of(payment));

    applier.apply(new YooKassaPayment("yk-1", "canceled", false, 1000, null));

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.CANCELED);
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void apply_doesNothingWhenStillPendingAtProvider() {
    Payment payment = payment(PaymentStatus.PENDING, 1000);
    when(paymentRepository.findByProviderPaymentIdForUpdate("yk-1"))
        .thenReturn(Optional.of(payment));

    applier.apply(new YooKassaPayment("yk-1", "pending", false, 1000, null));

    verify(paymentRepository, never()).save(any());
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void apply_doesNotActivateOnAmountMismatch() {
    Payment payment = payment(PaymentStatus.PENDING, 1000);
    when(paymentRepository.findByProviderPaymentIdForUpdate("yk-1"))
        .thenReturn(Optional.of(payment));

    applier.apply(new YooKassaPayment("yk-1", "succeeded", true, 999, null));

    verify(paymentRepository, never()).save(any());
    verify(subscriptionService, never()).activateOnPlan(any(), any(), anyInt());
  }

  @Test
  void apply_marksSucceededAndActivatesSubscription() {
    Payment payment = payment(PaymentStatus.PENDING, 1000);
    when(paymentRepository.findByProviderPaymentIdForUpdate("yk-1"))
        .thenReturn(Optional.of(payment));

    applier.apply(new YooKassaPayment("yk-1", "succeeded", true, 1000, null));

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    Payment saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(saved.getPaidAt()).isNotNull();

    verify(subscriptionService).activateOnPlan(payment.getUserId(), payment.getPlanId(), 30);
  }

  private static Payment payment(PaymentStatus status, long amountKopecks) {
    Payment payment = new Payment();
    setId(payment);
    payment.setUserId(UUID.randomUUID());
    payment.setPlanId(UUID.randomUUID());
    payment.setProviderPaymentId("yk-1");
    payment.setAmountKopecks(amountKopecks);
    payment.setStatus(status);
    return payment;
  }

  private static void setId(Payment payment) {
    try {
      var field = Payment.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(payment, UUID.randomUUID());
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
