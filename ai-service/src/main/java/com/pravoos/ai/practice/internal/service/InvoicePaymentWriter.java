package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.client.YooKassaInvoicePayment;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.InvoicePayment;
import com.pravoos.ai.practice.internal.repository.jpa.InvoicePaymentRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import com.pravoos.ai.shared.model.enums.InvoicePaymentStatus;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoicePaymentWriter {

  private static final Logger log = LoggerFactory.getLogger(InvoicePaymentWriter.class);

  private final InvoiceRepository invoiceRepository;
  private final InvoicePaymentRepository invoicePaymentRepository;
  private final InvoicePaidPublisher invoicePaidPublisher;

  public InvoicePaymentWriter(
      InvoiceRepository invoiceRepository,
      InvoicePaymentRepository invoicePaymentRepository,
      InvoicePaidPublisher invoicePaidPublisher) {
    this.invoiceRepository = invoiceRepository;
    this.invoicePaymentRepository = invoicePaymentRepository;
    this.invoicePaidPublisher = invoicePaidPublisher;
  }

  @Transactional(readOnly = true)
  public boolean isPending(String providerPaymentId) {
    InvoicePayment invoicePayment =
        invoicePaymentRepository.findByProviderPaymentId(providerPaymentId).orElse(null);
    if (invoicePayment == null) {
      log.warn("Received YooKassa webhook for unknown payment {}", providerPaymentId);
      return false;
    }
    if (invoicePayment.getStatus() != InvoicePaymentStatus.PENDING) {
      log.info(
          "Ignoring webhook for payment {} already in status {}",
          providerPaymentId,
          invoicePayment.getStatus());
      return false;
    }
    return true;
  }

  @Transactional
  public void applySnapshot(String providerPaymentId, YooKassaInvoicePayment snapshot) {
    InvoicePayment invoicePayment =
        invoicePaymentRepository.findByProviderPaymentId(providerPaymentId).orElse(null);
    if (invoicePayment == null || invoicePayment.getStatus() != InvoicePaymentStatus.PENDING) {
      return;
    }
    if (snapshot.succeeded()) {
      invoicePayment.markSucceeded();
      Invoice invoice =
          invoiceRepository
              .findById(invoicePayment.getInvoiceId())
              .orElseThrow(() -> new InvoiceNotFoundException(invoicePayment.getInvoiceId()));
      if (invoice.getStatus().canTransitionTo(InvoiceStatus.PAID)) {
        invoice.setStatus(InvoiceStatus.PAID);
        invoicePaidPublisher.publish(invoice);
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
}
