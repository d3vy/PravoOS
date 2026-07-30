package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.client.YooKassaInvoiceClient;
import com.pravoos.ai.practice.internal.client.YooKassaInvoicePayment;
import com.pravoos.ai.practice.internal.dto.InvoicePaymentResponse;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.InvoicePayment;
import com.pravoos.ai.practice.internal.repository.jpa.InvoicePaymentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import com.pravoos.ai.shared.exception.InvoiceStateException;
import com.pravoos.ai.shared.model.enums.InvoicePaymentStatus;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoicePaymentService {

  private static final Logger log = LoggerFactory.getLogger(InvoicePaymentService.class);
  private static final BigDecimal KOPECKS_IN_RUBLE = BigDecimal.valueOf(100);

  private final InvoiceRepository invoiceRepository;
  private final InvoicePaymentRepository invoicePaymentRepository;
  private final YooKassaInvoiceClient yooKassaInvoiceClient;

  public InvoicePaymentService(
      InvoiceRepository invoiceRepository,
      InvoicePaymentRepository invoicePaymentRepository,
      YooKassaInvoiceClient yooKassaInvoiceClient) {
    this.invoiceRepository = invoiceRepository;
    this.invoicePaymentRepository = invoicePaymentRepository;
    this.yooKassaInvoiceClient = yooKassaInvoiceClient;
  }

  @Transactional
  public InvoicePaymentResponse createPayment(UUID invoiceId, List<UUID> clientIds) {
    Invoice invoice = requireClientInvoice(invoiceId, clientIds);
    if (invoice.getStatus() != InvoiceStatus.ISSUED) {
      throw new InvoiceStateException("Only issued invoices can be paid online");
    }

    long amountKopecks =
        invoice.getTotal().multiply(KOPECKS_IN_RUBLE).setScale(0, RoundingMode.HALF_UP).longValue();
    String description = "Оплата счёта № " + invoice.getNumber();
    YooKassaInvoicePayment payment =
        yooKassaInvoiceClient.createPayment(
            invoiceId, amountKopecks, description, UUID.randomUUID().toString());

    invoicePaymentRepository.save(
        new InvoicePayment(invoiceId, payment.id(), amountKopecks, payment.confirmationUrl()));
    log.info(
        "Payment {} created for invoice {} ({} kopecks)", payment.id(), invoiceId, amountKopecks);
    return new InvoicePaymentResponse(invoiceId, payment.confirmationUrl());
  }

  @Transactional
  public void handleWebhook(String providerPaymentId) {
    InvoicePayment invoicePayment =
        invoicePaymentRepository.findByProviderPaymentId(providerPaymentId).orElse(null);
    if (invoicePayment == null) {
      log.warn("Received YooKassa webhook for unknown payment {}", providerPaymentId);
      return;
    }
    if (invoicePayment.getStatus() != InvoicePaymentStatus.PENDING) {
      log.info(
          "Ignoring webhook for payment {} already in status {}",
          providerPaymentId,
          invoicePayment.getStatus());
      return;
    }

    YooKassaInvoicePayment snapshot = yooKassaInvoiceClient.getPayment(providerPaymentId);
    if (snapshot.succeeded()) {
      invoicePayment.markSucceeded();
      Invoice invoice =
          invoiceRepository
              .findById(invoicePayment.getInvoiceId())
              .orElseThrow(() -> new InvoiceNotFoundException(invoicePayment.getInvoiceId()));
      if (invoice.getStatus().canTransitionTo(InvoiceStatus.PAID)) {
        invoice.setStatus(InvoiceStatus.PAID);
      }
      log.info("Payment {} succeeded for invoice {}", providerPaymentId, invoice.getId());
    } else if (snapshot.canceled()) {
      invoicePayment.markCanceled();
      log.info(
          "Payment {} canceled for invoice {}", providerPaymentId, invoicePayment.getInvoiceId());
    } else {
      log.info(
          "Payment {} still in status {} for invoice {}",
          providerPaymentId,
          snapshot.status(),
          invoicePayment.getInvoiceId());
    }
  }

  private Invoice requireClientInvoice(UUID invoiceId, List<UUID> clientIds) {
    if (clientIds == null || clientIds.isEmpty()) {
      throw new InvoiceNotFoundException(invoiceId);
    }
    return invoiceRepository
        .findByIdAndClientIdIn(invoiceId, clientIds)
        .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
  }
}
