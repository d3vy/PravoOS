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

@Service
public class InvoicePaymentService {

  private static final Logger log = LoggerFactory.getLogger(InvoicePaymentService.class);
  private static final BigDecimal KOPECKS_IN_RUBLE = BigDecimal.valueOf(100);

  private final InvoiceRepository invoiceRepository;
  private final InvoicePaymentRepository invoicePaymentRepository;
  private final YooKassaInvoiceClient yooKassaInvoiceClient;
  private final InvoicePaymentWriter invoicePaymentWriter;

  public InvoicePaymentService(
      InvoiceRepository invoiceRepository,
      InvoicePaymentRepository invoicePaymentRepository,
      YooKassaInvoiceClient yooKassaInvoiceClient,
      InvoicePaymentWriter invoicePaymentWriter) {
    this.invoiceRepository = invoiceRepository;
    this.invoicePaymentRepository = invoicePaymentRepository;
    this.yooKassaInvoiceClient = yooKassaInvoiceClient;
    this.invoicePaymentWriter = invoicePaymentWriter;
  }

  public InvoicePaymentResponse createPayment(UUID invoiceId, List<UUID> clientIds) {
    Invoice invoice = requireClientInvoice(invoiceId, clientIds);
    if (invoice.getStatus() != InvoiceStatus.ISSUED) {
      throw new InvoiceStateException("Only issued invoices can be paid online");
    }

    long amountKopecks =
        invoice.getTotal().multiply(KOPECKS_IN_RUBLE).setScale(0, RoundingMode.HALF_UP).longValue();

    InvoicePayment reusable = findReusablePending(invoiceId, amountKopecks);
    if (reusable != null) {
      log.info(
          "Reusing pending payment {} for invoice {} instead of creating a second one",
          reusable.getProviderPaymentId(),
          invoiceId);
      return new InvoicePaymentResponse(invoiceId, reusable.getConfirmationUrl());
    }

    String description = "Оплата счёта № " + invoice.getNumber();
    YooKassaInvoicePayment payment =
        yooKassaInvoiceClient.createPayment(
            invoiceId, amountKopecks, description, UUID.randomUUID().toString());

    invoicePaymentWriter.savePending(invoiceId, payment, amountKopecks);
    log.info(
        "Payment {} created for invoice {} ({} kopecks)", payment.id(), invoiceId, amountKopecks);
    return new InvoicePaymentResponse(invoiceId, payment.confirmationUrl());
  }

  public void handleWebhook(String providerPaymentId) {
    if (!invoicePaymentWriter.isPending(providerPaymentId)) {
      return;
    }
    YooKassaInvoicePayment snapshot = yooKassaInvoiceClient.getPayment(providerPaymentId);
    invoicePaymentWriter.applySnapshot(providerPaymentId, snapshot);
  }

  private InvoicePayment findReusablePending(UUID invoiceId, long amountKopecks) {
    return invoicePaymentRepository
        .findByInvoiceIdAndStatusOrderByCreatedAtDesc(invoiceId, InvoicePaymentStatus.PENDING)
        .stream()
        .filter(payment -> payment.getAmountKopecks() == amountKopecks)
        .filter(payment -> payment.getConfirmationUrl() != null)
        .findFirst()
        .orElse(null);
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
