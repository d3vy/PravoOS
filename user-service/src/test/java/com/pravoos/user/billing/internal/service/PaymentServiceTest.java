package com.pravoos.user.billing.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.user.billing.internal.client.YooKassaClient;
import com.pravoos.user.billing.internal.client.YooKassaPayment;
import com.pravoos.user.billing.internal.dto.CheckoutResponse;
import com.pravoos.user.billing.internal.dto.PaymentResponse;
import com.pravoos.user.billing.internal.model.entity.Payment;
import com.pravoos.user.billing.internal.model.entity.Plan;
import com.pravoos.user.billing.internal.model.enums.PaymentStatus;
import com.pravoos.user.billing.internal.repository.PaymentRepository;
import com.pravoos.user.billing.internal.repository.PlanRepository;
import com.pravoos.user.shared.exception.PravoosException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private PlanRepository planRepository;
  @Mock private PaymentApplier paymentApplier;
  @Mock private YooKassaClient yooKassaClient;

  private PaymentService service;

  @BeforeEach
  void setUp() {
    service = new PaymentService(paymentRepository, planRepository, paymentApplier, yooKassaClient);
  }

  @Test
  void startCheckout_throwsWhenPlanNotFound() {
    UUID userId = UUID.randomUUID();
    when(planRepository.findByCode("pro")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.startCheckout(userId, "pro"))
        .isInstanceOf(PravoosException.class)
        .extracting(ex -> ((PravoosException) ex).getCode())
        .isEqualTo("PLAN_NOT_FOUND");

    verify(yooKassaClient, never()).createPayment(any(), any(), anyLong(), any(), any());
  }

  @Test
  void startCheckout_throwsWhenPlanIsFree() {
    UUID userId = UUID.randomUUID();
    when(planRepository.findByCode("free")).thenReturn(Optional.of(plan("free", 0)));

    assertThatThrownBy(() -> service.startCheckout(userId, "free"))
        .isInstanceOf(PravoosException.class)
        .extracting(ex -> ((PravoosException) ex).getCode())
        .isEqualTo("PLAN_NOT_PAYABLE");

    verify(yooKassaClient, never()).createPayment(any(), any(), anyLong(), any(), any());
  }

  @Test
  void startCheckout_createsPaymentAndPersistsPendingPayment() {
    UUID userId = UUID.randomUUID();
    Plan plan = plan("pro", 99000);
    when(planRepository.findByCode("pro")).thenReturn(Optional.of(plan));
    YooKassaPayment created =
        new YooKassaPayment(
            "yk-123", "pending", false, 99000, "https://yookassa.ru/confirm/yk-123");
    when(yooKassaClient.createPayment(eq(userId), eq("pro"), eq(99000L), any(), any()))
        .thenReturn(created);

    CheckoutResponse response = service.startCheckout(userId, "pro");

    ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository).save(captor.capture());
    Payment saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getPlanId()).isEqualTo(plan.getId());
    assertThat(saved.getProviderPaymentId()).isEqualTo("yk-123");
    assertThat(saved.getAmountKopecks()).isEqualTo(99000);
    assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(saved.getConfirmationUrl()).isEqualTo("https://yookassa.ru/confirm/yk-123");

    assertThat(response.confirmationUrl()).isEqualTo("https://yookassa.ru/confirm/yk-123");
  }

  @Test
  void startCheckout_usesDistinctIdempotenceKeyPerCall() {
    UUID userId = UUID.randomUUID();
    Plan plan = plan("pro", 50000);
    when(planRepository.findByCode("pro")).thenReturn(Optional.of(plan));
    when(yooKassaClient.createPayment(any(), any(), anyLong(), any(), any()))
        .thenReturn(new YooKassaPayment("yk-1", "pending", false, 50000, "url"))
        .thenReturn(new YooKassaPayment("yk-2", "pending", false, 50000, "url"));

    service.startCheckout(userId, "pro");
    service.startCheckout(userId, "pro");

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(yooKassaClient, times(2))
        .createPayment(eq(userId), eq("pro"), eq(50000L), any(), keyCaptor.capture());
    List<String> keys = keyCaptor.getAllValues();
    assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
  }

  @Test
  void handleNotification_fetchesPaymentAndDelegatesToApplier() {
    YooKassaPayment snapshot = new YooKassaPayment("yk-1", "succeeded", true, 1000, null);
    when(yooKassaClient.getPayment("yk-1")).thenReturn(snapshot);

    service.handleNotification("yk-1");

    verify(paymentApplier).apply(snapshot);
  }

  @Test
  void history_returnsEmptyListWhenNoPayments() {
    UUID userId = UUID.randomUUID();
    when(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
    when(planRepository.findAllById(any())).thenReturn(List.of());

    List<PaymentResponse> result = service.history(userId);

    assertThat(result).isEmpty();
  }

  @Test
  void history_mapsPaymentsWithPlanCodesInOrder() {
    UUID userId = UUID.randomUUID();
    Plan proPlan = plan("pro", 99000);
    Plan basicPlan = plan("basic", 49000);

    Payment succeeded = payment(userId, proPlan.getId(), PaymentStatus.SUCCEEDED);
    Payment pending = payment(userId, basicPlan.getId(), PaymentStatus.PENDING);

    when(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId))
        .thenReturn(List.of(succeeded, pending));
    when(planRepository.findAllById(any())).thenReturn(List.of(proPlan, basicPlan));

    List<PaymentResponse> result = service.history(userId);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo(succeeded.getId());
    assertThat(result.get(0).planCode()).isEqualTo("pro");
    assertThat(result.get(0).status()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(result.get(1).id()).isEqualTo(pending.getId());
    assertThat(result.get(1).planCode()).isEqualTo("basic");
  }

  @Test
  void history_toleratesMissingPlan() {
    UUID userId = UUID.randomUUID();
    Payment orphaned = payment(userId, UUID.randomUUID(), PaymentStatus.SUCCEEDED);
    when(paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(orphaned));
    when(planRepository.findAllById(any())).thenReturn(List.of());

    List<PaymentResponse> result = service.history(userId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).planCode()).isNull();
  }

  private static Plan plan(String code, long priceKopecks) {
    Plan plan = new Plan();
    setId(plan);
    plan.setCode(code);
    plan.setName(code);
    plan.setPriceKopecks(priceKopecks);
    return plan;
  }

  private static Payment payment(UUID userId, UUID planId, PaymentStatus status) {
    Payment payment = new Payment();
    setId(payment);
    payment.setUserId(userId);
    payment.setPlanId(planId);
    payment.setProviderPaymentId(UUID.randomUUID().toString());
    payment.setAmountKopecks(1000);
    payment.setStatus(status);
    if (status == PaymentStatus.SUCCEEDED) {
      payment.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
    }
    return payment;
  }

  private static void setId(Object entity) {
    try {
      var field = entity.getClass().getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, UUID.randomUUID());
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
