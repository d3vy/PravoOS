package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class InvoicePaymentServiceTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoicePaymentRepository invoicePaymentRepository;
  @Mock private YooKassaInvoiceClient yooKassaInvoiceClient;

  @Mock private InvoicePaidPublisher invoicePaidPublisher;

  private InvoicePaymentService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    InvoicePaymentWriter invoicePaymentWriter =
        new InvoicePaymentWriter(invoiceRepository, invoicePaymentRepository, invoicePaidPublisher);
    service =
        new InvoicePaymentService(
            invoiceRepository,
            invoicePaymentRepository,
            yooKassaInvoiceClient,
            invoicePaymentWriter);
  }

  @Test
  void createPayment_issuedInvoiceCreatesYooKassaPaymentAndStoresIt() {
    Invoice invoice = invoice(InvoiceStatus.ISSUED);
    when(invoiceRepository.findByIdAndClientIdIn(invoice.getId(), List.of(clientId)))
        .thenReturn(Optional.of(invoice));
    YooKassaInvoicePayment payment =
        new YooKassaInvoicePayment("pay_1", "pending", false, 150000L, "https://yookassa/confirm");
    when(yooKassaInvoiceClient.createPayment(
            eq(invoice.getId()), anyLong(), anyString(), anyString()))
        .thenReturn(payment);

    InvoicePaymentResponse response = service.createPayment(invoice.getId(), List.of(clientId));

    assertThat(response.confirmationUrl()).isEqualTo("https://yookassa/confirm");
    ArgumentCaptor<InvoicePayment> captor = ArgumentCaptor.forClass(InvoicePayment.class);
    verify(invoicePaymentRepository).save(captor.capture());
    assertThat(captor.getValue().getProviderPaymentId()).isEqualTo("pay_1");
    assertThat(captor.getValue().getAmountKopecks()).isEqualTo(150000L);
  }

  @Test
  void createPayment_rejectsInvoiceNotIssued() {
    Invoice invoice = invoice(InvoiceStatus.DRAFT);
    when(invoiceRepository.findByIdAndClientIdIn(invoice.getId(), List.of(clientId)))
        .thenReturn(Optional.of(invoice));

    assertThatThrownBy(() -> service.createPayment(invoice.getId(), List.of(clientId)))
        .isInstanceOf(InvoiceStateException.class);
    verifyNoInteractions(yooKassaInvoiceClient);
  }

  @Test
  void createPayment_rejectsInvoiceNotOwnedByClient() {
    UUID invoiceId = UUID.randomUUID();
    when(invoiceRepository.findByIdAndClientIdIn(invoiceId, List.of(clientId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.createPayment(invoiceId, List.of(clientId)))
        .isInstanceOf(InvoiceNotFoundException.class);
  }

  @Test
  void handleWebhook_succeededPaymentMarksInvoicePaid() {
    Invoice invoice = invoice(InvoiceStatus.ISSUED);
    InvoicePayment invoicePayment = new InvoicePayment(invoice.getId(), "pay_1", 150000L, "url");
    when(invoicePaymentRepository.findByProviderPaymentId("pay_1"))
        .thenReturn(Optional.of(invoicePayment));
    when(yooKassaInvoiceClient.getPayment("pay_1"))
        .thenReturn(new YooKassaInvoicePayment("pay_1", "succeeded", true, 150000L, null));
    when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

    service.handleWebhook("pay_1");

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    assertThat(invoicePayment.getPaidAt()).isNotNull();
  }

  @Test
  void handleWebhook_leavesInvoiceUnpaidWhenTheSettledAmountDiffersFromTheExpectedOne() {
    Invoice invoice = invoice(InvoiceStatus.ISSUED);
    InvoicePayment invoicePayment = new InvoicePayment(invoice.getId(), "pay_1", 150000L, "url");
    when(invoicePaymentRepository.findByProviderPaymentId("pay_1"))
        .thenReturn(Optional.of(invoicePayment));
    when(yooKassaInvoiceClient.getPayment("pay_1"))
        .thenReturn(new YooKassaInvoicePayment("pay_1", "succeeded", true, 100000L, null));

    service.handleWebhook("pay_1");

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    assertThat(invoicePayment.getStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
    verify(invoiceRepository, never()).findById(any());
  }

  @Test
  void handleWebhook_canceledPaymentDoesNotTouchInvoice() {
    Invoice invoice = invoice(InvoiceStatus.ISSUED);
    InvoicePayment invoicePayment = new InvoicePayment(invoice.getId(), "pay_1", 150000L, "url");
    when(invoicePaymentRepository.findByProviderPaymentId("pay_1"))
        .thenReturn(Optional.of(invoicePayment));
    when(yooKassaInvoiceClient.getPayment("pay_1"))
        .thenReturn(new YooKassaInvoicePayment("pay_1", "canceled", false, 150000L, null));

    service.handleWebhook("pay_1");

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    verify(invoiceRepository, never()).findById(any());
  }

  @Test
  void handleWebhook_ignoresUnknownPayment() {
    when(invoicePaymentRepository.findByProviderPaymentId("unknown")).thenReturn(Optional.empty());

    service.handleWebhook("unknown");

    verifyNoInteractions(yooKassaInvoiceClient);
  }

  private Invoice invoice(InvoiceStatus status) {
    Invoice invoice = new Invoice();
    setField(invoice, "id", UUID.randomUUID());
    invoice.setLawyerId(lawyerId);
    invoice.setClientId(clientId);
    invoice.setNumber("СЧ-2026-0001");
    invoice.setStatus(status);
    invoice.setIssueDate(LocalDate.of(2026, 7, 1));
    invoice.setSubtotal(new BigDecimal("1500.00"));
    invoice.setTotal(new BigDecimal("1500.00"));
    return invoice;
  }

  private void setField(Object target, String name, Object value) {
    try {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
