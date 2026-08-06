package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.event.InvoicePaidKafkaPayload;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InvoicePaidPublisherTest {

  @Mock private ClientRepository clientRepository;
  @Mock private OutboxEventService outboxEventService;

  @InjectMocks private InvoicePaidPublisher invoicePaidPublisher;

  @Test
  void publishEnqueuesEventWithFormattedTotalAndClientName() {
    UUID invoiceId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    Invoice invoice = new Invoice();
    ReflectionTestUtils.setField(invoice, "id", invoiceId);
    invoice.setLawyerId(lawyerId);
    invoice.setClientId(clientId);
    invoice.setNumber("INV-001");
    invoice.setCurrency("RUB");
    invoice.setTotal(new BigDecimal("12345.6"));

    Client client = new Client();
    client.setName("ООО Ромашка");
    when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

    invoicePaidPublisher.publish(invoice);

    ArgumentCaptor<InvoicePaidKafkaPayload> payloadCaptor =
        ArgumentCaptor.forClass(InvoicePaidKafkaPayload.class);
    verify(outboxEventService)
        .enqueue(eq("invoice.paid"), eq(invoiceId.toString()), payloadCaptor.capture());

    InvoicePaidKafkaPayload payload = payloadCaptor.getValue();
    assertThat(payload.invoiceId()).isEqualTo(invoiceId);
    assertThat(payload.lawyerId()).isEqualTo(lawyerId);
    assertThat(payload.invoiceNumber()).isEqualTo("INV-001");
    assertThat(payload.clientName()).isEqualTo("ООО Ромашка");
    assertThat(payload.totalFormatted()).isEqualTo("12,345.60 RUB");
  }

  @Test
  void publishFallsBackToDefaultClientNameWhenClientMissing() {
    UUID invoiceId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    Invoice invoice = new Invoice();
    ReflectionTestUtils.setField(invoice, "id", invoiceId);
    invoice.setLawyerId(UUID.randomUUID());
    invoice.setClientId(clientId);
    invoice.setNumber("INV-002");
    invoice.setCurrency("RUB");
    invoice.setTotal(BigDecimal.TEN);

    when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

    invoicePaidPublisher.publish(invoice);

    ArgumentCaptor<InvoicePaidKafkaPayload> payloadCaptor =
        ArgumentCaptor.forClass(InvoicePaidKafkaPayload.class);
    verify(outboxEventService).enqueue(any(), any(), payloadCaptor.capture());

    assertThat(payloadCaptor.getValue().clientName()).isEqualTo("Клиент");
  }
}
