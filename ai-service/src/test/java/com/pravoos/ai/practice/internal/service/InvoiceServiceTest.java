package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.CreateInvoiceRequest;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.practice.internal.repository.jpa.TimeEntryRepository;
import com.pravoos.ai.shared.exception.InvoiceStateException;
import com.pravoos.ai.shared.exception.NoBillableTimeException;
import com.pravoos.ai.shared.model.enums.ClientType;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ClientService clientService;
    @Mock private InvoicePdfWriter invoicePdfWriter;

    private InvoiceService service;

    private final UUID lawyerId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        InvoiceNumberGenerator numberGenerator = new InvoiceNumberGenerator(invoiceRepository);
        service = new InvoiceService(invoiceRepository, timeEntryRepository, clientRepository,
                clientService, numberGenerator, invoicePdfWriter);
        lenient().when(invoiceRepository.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice invoice = invocation.getArgument(0);
            setField(invoice, "id", UUID.randomUUID());
            return invoice;
        });
        lenient().when(clientRepository.findById(clientId)).thenReturn(Optional.of(client()));
    }

    @Test
    void create_buildsLinesMarksEntriesAndComputesTotal() {
        when(clientService.requireOwnedClient(clientId, lawyerId)).thenReturn(client());
        TimeEntry first = entry(60, new BigDecimal("3000"));
        TimeEntry second = entry(30, new BigDecimal("4000"));
        when(timeEntryRepository.lockBillableForClient(clientId))
                .thenReturn(List.of(first, second));

        CreateInvoiceRequest request = new CreateInvoiceRequest(clientId, null, null,
                LocalDate.of(2026, 8, 1), "Оплата услуг");
        InvoiceResponse response = service.create(request, lawyerId);

        assertThat(response.lines()).hasSize(2);
        assertThat(response.total()).isEqualByComparingTo("5000.00");
        assertThat(response.number()).isEqualTo("СЧ-" + LocalDate.now().getYear() + "-0001");
        assertThat(first.getInvoiceId()).isNotNull();
        assertThat(second.getInvoiceId()).isEqualTo(first.getInvoiceId());
    }

    @Test
    void create_throwsWhenNoBillableTime() {
        when(clientService.requireOwnedClient(clientId, lawyerId)).thenReturn(client());
        when(timeEntryRepository.lockBillableForClient(clientId))
                .thenReturn(List.of());

        CreateInvoiceRequest request = new CreateInvoiceRequest(clientId, null, null, null, null);
        assertThatThrownBy(() -> service.create(request, lawyerId))
                .isInstanceOf(NoBillableTimeException.class);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_withExplicitIdsBillsOnlyLockedEntries() {
        when(clientService.requireOwnedClient(clientId, lawyerId)).thenReturn(client());
        TimeEntry valid = entry(60, new BigDecimal("2000"));
        TimeEntry foreignClient = entry(60, new BigDecimal("2000"));
        foreignClient.setClientId(UUID.randomUUID());
        TimeEntry alreadyInvoiced = entry(60, new BigDecimal("2000"));
        alreadyInvoiced.setInvoiceId(UUID.randomUUID());
        List<UUID> ids = List.of(valid.getId(), foreignClient.getId(), alreadyInvoiced.getId());
        when(timeEntryRepository.lockBillableByIds(ids, lawyerId, clientId)).thenReturn(List.of(valid));

        CreateInvoiceRequest request = new CreateInvoiceRequest(clientId, null, ids, null, null);
        InvoiceResponse response = service.create(request, lawyerId);

        assertThat(response.lines()).hasSize(1);
        assertThat(response.total()).isEqualByComparingTo("2000.00");
    }

    @Test
    void updateStatus_movesDraftToIssued() {
        Invoice invoice = savedInvoice(InvoiceStatus.DRAFT);
        when(invoiceRepository.findByIdAndLawyerId(invoice.getId(), lawyerId)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = service.updateStatus(invoice.getId(), lawyerId, InvoiceStatus.ISSUED);

        assertThat(response.status()).isEqualTo(InvoiceStatus.ISSUED);
        verify(timeEntryRepository, never()).releaseByInvoiceId(any());
    }

    @Test
    void updateStatus_rejectsIllegalTransition() {
        Invoice invoice = savedInvoice(InvoiceStatus.PAID);
        when(invoiceRepository.findByIdAndLawyerId(invoice.getId(), lawyerId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.updateStatus(invoice.getId(), lawyerId, InvoiceStatus.ISSUED))
                .isInstanceOf(InvoiceStateException.class);
    }

    @Test
    void updateStatus_cancelReleasesTimeEntries() {
        Invoice invoice = savedInvoice(InvoiceStatus.ISSUED);
        when(invoiceRepository.findByIdAndLawyerId(invoice.getId(), lawyerId)).thenReturn(Optional.of(invoice));

        service.updateStatus(invoice.getId(), lawyerId, InvoiceStatus.CANCELED);

        verify(timeEntryRepository).releaseByInvoiceId(invoice.getId());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELED);
    }

    @Test
    void delete_rejectsNonDraft() {
        Invoice invoice = savedInvoice(InvoiceStatus.ISSUED);
        when(invoiceRepository.findByIdAndLawyerId(invoice.getId(), lawyerId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.delete(invoice.getId(), lawyerId))
                .isInstanceOf(InvoiceStateException.class);
        verify(invoiceRepository, never()).delete(any());
    }

    @Test
    void delete_draftReleasesEntriesAndRemovesInvoice() {
        Invoice invoice = savedInvoice(InvoiceStatus.DRAFT);
        when(invoiceRepository.findByIdAndLawyerId(invoice.getId(), lawyerId)).thenReturn(Optional.of(invoice));

        service.delete(invoice.getId(), lawyerId);

        verify(timeEntryRepository).releaseByInvoiceId(invoice.getId());
        verify(invoiceRepository).delete(invoice);
    }

    private Invoice savedInvoice(InvoiceStatus status) {
        Invoice invoice = new Invoice();
        setField(invoice, "id", UUID.randomUUID());
        invoice.setLawyerId(lawyerId);
        invoice.setClientId(clientId);
        invoice.setNumber("СЧ-2026-0001");
        invoice.setStatus(status);
        invoice.setIssueDate(LocalDate.of(2026, 7, 15));
        invoice.setSubtotal(new BigDecimal("1000.00"));
        invoice.setTotal(new BigDecimal("1000.00"));
        return invoice;
    }

    private Client client() {
        Client client = new Client();
        setField(client, "id", clientId);
        client.setLawyerId(lawyerId);
        client.setName("ООО Ромашка");
        client.setType(ClientType.COMPANY);
        return client;
    }

    private TimeEntry entry(int minutes, BigDecimal rate) {
        TimeEntry entry = new TimeEntry();
        setField(entry, "id", UUID.randomUUID());
        entry.setCaseId(UUID.randomUUID());
        entry.setClientId(clientId);
        entry.setLawyerId(lawyerId);
        entry.setDescription("Работа");
        entry.setActivityDate(LocalDate.of(2026, 7, 15));
        entry.setMinutes(minutes);
        entry.setHourlyRate(rate);
        entry.setBillable(true);
        return entry;
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
