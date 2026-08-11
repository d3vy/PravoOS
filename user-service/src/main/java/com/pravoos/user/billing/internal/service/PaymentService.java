package com.pravoos.user.billing.internal.service;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentRepository paymentRepository;
  private final PlanRepository planRepository;
  private final PaymentApplier paymentApplier;
  private final YooKassaClient yooKassaClient;

  public PaymentService(
      PaymentRepository paymentRepository,
      PlanRepository planRepository,
      PaymentApplier paymentApplier,
      YooKassaClient yooKassaClient) {
    this.paymentRepository = paymentRepository;
    this.planRepository = planRepository;
    this.paymentApplier = paymentApplier;
    this.yooKassaClient = yooKassaClient;
  }

  public CheckoutResponse startCheckout(UUID userId, String planCode) {
    Plan plan =
        planRepository
            .findByCode(planCode)
            .orElseThrow(
                () ->
                    new PravoosException(
                        "Тариф не найден: " + planCode, HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND"));
    if (plan.getPriceKopecks() <= 0) {
      throw new PravoosException(
          "Бесплатный тариф не требует оплаты", HttpStatus.BAD_REQUEST, "PLAN_NOT_PAYABLE");
    }

    Payment reusable = findReusablePending(userId, plan);
    if (reusable != null) {
      log.info(
          "Reusing pending payment {} for user {} on plan {} instead of creating a second one",
          reusable.getProviderPaymentId(),
          userId,
          planCode);
      return new CheckoutResponse(reusable.getId(), reusable.getConfirmationUrl());
    }

    YooKassaPayment created =
        yooKassaClient.createPayment(
            userId,
            plan.getCode(),
            plan.getPriceKopecks(),
            "Подписка PravoOS — " + plan.getName(),
            UUID.randomUUID().toString());

    Payment payment = new Payment();
    payment.setUserId(userId);
    payment.setPlanId(plan.getId());
    payment.setProviderPaymentId(created.id());
    payment.setAmountKopecks(plan.getPriceKopecks());
    payment.setStatus(PaymentStatus.PENDING);
    payment.setConfirmationUrl(created.confirmationUrl());
    paymentRepository.save(payment);

    log.info(
        "Checkout started for user {} on plan {} (payment {})", userId, planCode, created.id());
    return new CheckoutResponse(payment.getId(), created.confirmationUrl());
  }

  private Payment findReusablePending(UUID userId, Plan plan) {
    return paymentRepository
        .findByUserIdAndPlanIdAndStatusOrderByCreatedAtDesc(
            userId, plan.getId(), PaymentStatus.PENDING)
        .stream()
        .filter(payment -> payment.getAmountKopecks() == plan.getPriceKopecks())
        .filter(payment -> payment.getConfirmationUrl() != null)
        .findFirst()
        .orElse(null);
  }

  public void handleNotification(String providerPaymentId) {
    paymentApplier.apply(yooKassaClient.getPayment(providerPaymentId));
  }

  @Transactional(readOnly = true)
  public List<PaymentResponse> history(UUID userId) {
    List<Payment> payments = paymentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    Map<UUID, String> planCodes =
        planRepository
            .findAllById(payments.stream().map(Payment::getPlanId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Plan::getId, Plan::getCode));

    return payments.stream()
        .map(
            payment ->
                new PaymentResponse(
                    payment.getId(),
                    planCodes.get(payment.getPlanId()),
                    payment.getAmountKopecks(),
                    payment.getStatus(),
                    payment.getConfirmationUrl(),
                    payment.getPaidAt(),
                    payment.getCreatedAt()))
        .toList();
  }
}
