package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortalInvoiceServiceTest {

  @Mock private InvoiceRepository invoiceRepository;
  @Mock private ClientRepository clientRepository;

  private PortalInvoiceService service;

  private final UUID clientId = UUID.randomUUID();
  private final UUID invoiceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = new PortalInvoiceService(invoiceRepository, clientRepository);
  }

  private Invoice invoice() {
    Invoice invoice = new Invoice();
    invoice.setClientId(clientId);
    invoice.setNumber("СЧ-2026-0001");
    invoice.setStatus(InvoiceStatus.ISSUED);
    invoice.setIssueDate(LocalDate.of(2026, 7, 15));
    invoice.setSubtotal(new BigDecimal("1000.00"));
    invoice.setTotal(new BigDecimal("1000.00"));
    return invoice;
  }

  @Test
  void listInvoicesReturnsEmptyWithoutRepositoryCallWhenClientIdsNull() {
    List<InvoiceSummary> result = service.listInvoices(null);

    assertThat(result).isEmpty();
    verifyNoInteractions(invoiceRepository, clientRepository);
  }

  @Test
  void listInvoicesReturnsEmptyWithoutRepositoryCallWhenClientIdsEmpty() {
    List<InvoiceSummary> result = service.listInvoices(List.of());

    assertThat(result).isEmpty();
    verifyNoInteractions(invoiceRepository, clientRepository);
  }

  @Test
  void listInvoicesMapsWithResolvedClientName() {
    Invoice invoice = invoice();
    Client client = new Client();
    client.setName("Иванов И.И.");
    when(invoiceRepository.findByClientIdInOrderByCreatedAtDesc(List.of(clientId)))
        .thenReturn(List.of(invoice));
    when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

    List<InvoiceSummary> result = service.listInvoices(List.of(clientId));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).clientName()).isEqualTo("Иванов И.И.");
    assertThat(result.get(0).number()).isEqualTo("СЧ-2026-0001");
  }

  @Test
  void listInvoicesMapsNullClientNameWhenClientMissing() {
    Invoice invoice = invoice();
    when(invoiceRepository.findByClientIdInOrderByCreatedAtDesc(List.of(clientId)))
        .thenReturn(List.of(invoice));
    when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

    List<InvoiceSummary> result = service.listInvoices(List.of(clientId));

    assertThat(result.get(0).clientName()).isNull();
  }

  @Test
  void getInvoiceThrowsWhenClientIdsNull() {
    assertThatThrownBy(() -> service.getInvoice(invoiceId, null))
        .isInstanceOf(InvoiceNotFoundException.class);
    verifyNoInteractions(invoiceRepository);
  }

  @Test
  void getInvoiceThrowsWhenClientIdsEmpty() {
    assertThatThrownBy(() -> service.getInvoice(invoiceId, List.of()))
        .isInstanceOf(InvoiceNotFoundException.class);
    verifyNoInteractions(invoiceRepository);
  }

  @Test
  void getInvoiceThrowsWhenInvoiceNotFoundForClients() {
    when(invoiceRepository.findByIdAndClientIdIn(invoiceId, List.of(clientId)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getInvoice(invoiceId, List.of(clientId)))
        .isInstanceOf(InvoiceNotFoundException.class);
  }

  @Test
  void getInvoiceReturnsMappedResponseForOwnedClient() {
    Invoice invoice = invoice();
    Client client = new Client();
    client.setName("Петров П.П.");
    when(invoiceRepository.findByIdAndClientIdIn(invoiceId, List.of(clientId)))
        .thenReturn(Optional.of(invoice));
    when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

    InvoiceResponse response = service.getInvoice(invoiceId, List.of(clientId));

    assertThat(response.clientName()).isEqualTo("Петров П.П.");
    assertThat(response.number()).isEqualTo("СЧ-2026-0001");
    assertThat(response.total()).isEqualByComparingTo("1000.00");
  }
}
