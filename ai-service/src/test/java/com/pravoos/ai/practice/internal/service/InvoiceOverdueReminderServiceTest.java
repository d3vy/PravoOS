package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.InvoiceOverdueReminder;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceOverdueReminderRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.event.InvoiceOverdueKafkaPayload;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceOverdueReminderServiceTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private ClientRepository clientRepository;
  @Mock private InvoiceOverdueReminderRepository reminderRepository;
  @Mock private OutboxEventService outboxEventService;

  private InvoiceOverdueReminderService service;

  private final UUID lawyerId = UUID.randomUUID();
  private final UUID clientId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new InvoiceOverdueReminderService(
            invoiceRepository, clientRepository, reminderRepository, outboxEventService, null);
    lenientClientLookup();
  }

  private void lenientClientLookup() {
    org.mockito.Mockito.lenient()
        .when(clientRepository.findById(clientId))
        .thenReturn(Optional.of(client()));
  }

  @Test
  void enqueuesOverdueReminder_whenNotAlreadySent() {
    Invoice invoice = invoice();
    when(reminderRepository.existsByInvoiceIdAndThresholdDays(invoice.getId(), 3))
        .thenReturn(false);

    boolean result = service.enqueueReminder(invoice, 3, "ООО Ромашка");

    assertThat(result).isTrue();
    ArgumentCaptor<InvoiceOverdueReminder> reminderCaptor =
        ArgumentCaptor.forClass(InvoiceOverdueReminder.class);
    verify(reminderRepository).save(reminderCaptor.capture());
    assertThat(reminderCaptor.getValue().getInvoiceId()).isEqualTo(invoice.getId());
    assertThat(reminderCaptor.getValue().getThresholdDays()).isEqualTo(3);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventService)
        .enqueue(eq("invoice.overdue"), eq(invoice.getId().toString()), payloadCaptor.capture());
    InvoiceOverdueKafkaPayload payload = (InvoiceOverdueKafkaPayload) payloadCaptor.getValue();
    assertThat(payload.invoiceId()).isEqualTo(invoice.getId());
    assertThat(payload.lawyerId()).isEqualTo(lawyerId);
    assertThat(payload.clientName()).isEqualTo("ООО Ромашка");
    assertThat(payload.daysOverdue()).isEqualTo(3);
  }

  @Test
  void doesNotEnqueueOverdueReminder_whenAlreadySent() {
    Invoice invoice = invoice();
    when(reminderRepository.existsByInvoiceIdAndThresholdDays(invoice.getId(), 7)).thenReturn(true);

    boolean result = service.enqueueReminder(invoice, 7, "ООО Ромашка");

    assertThat(result).isFalse();
    verify(reminderRepository, never()).save(any());
    verify(outboxEventService, never()).enqueue(any(), any(), any());
  }

  private Invoice invoice() {
    Invoice invoice = new Invoice();
    setField(invoice, "id", UUID.randomUUID());
    invoice.setLawyerId(lawyerId);
    invoice.setClientId(clientId);
    invoice.setNumber("СЧ-2026-0007");
    invoice.setIssueDate(LocalDate.now().minusDays(30));
    invoice.setDueDate(LocalDate.now().minusDays(3));
    invoice.setSubtotal(new BigDecimal("2500.00"));
    invoice.setTotal(new BigDecimal("2500.00"));
    return invoice;
  }

  private Client client() {
    Client client = new Client();
    setField(client, "id", clientId);
    client.setLawyerId(lawyerId);
    client.setName("ООО Ромашка");
    return client;
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
