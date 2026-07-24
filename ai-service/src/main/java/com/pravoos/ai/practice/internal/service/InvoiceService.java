package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.CreateInvoiceRequest;
import com.pravoos.ai.practice.internal.dto.ExportedFile;
import com.pravoos.ai.practice.internal.dto.InvoiceExportModel;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.InvoiceLine;
import com.pravoos.ai.practice.internal.model.entity.TimeEntry;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.practice.internal.repository.jpa.TimeEntryRepository;
import com.pravoos.ai.practice.internal.util.BillingAmounts;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import com.pravoos.ai.shared.exception.InvoiceStateException;
import com.pravoos.ai.shared.exception.NoBillableTimeException;
import com.pravoos.ai.shared.model.enums.ExportFormat;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.util.PageRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
  private static final int NUMBER_RETRY_ATTEMPTS = 5;

  private final InvoiceRepository invoiceRepository;
  private final TimeEntryRepository timeEntryRepository;
  private final ClientRepository clientRepository;
  private final ClientService clientService;
  private final InvoiceNumberGenerator invoiceNumberGenerator;
  private final InvoicePdfWriter invoicePdfWriter;

  public InvoiceService(
      InvoiceRepository invoiceRepository,
      TimeEntryRepository timeEntryRepository,
      ClientRepository clientRepository,
      ClientService clientService,
      InvoiceNumberGenerator invoiceNumberGenerator,
      InvoicePdfWriter invoicePdfWriter) {
    this.invoiceRepository = invoiceRepository;
    this.timeEntryRepository = timeEntryRepository;
    this.clientRepository = clientRepository;
    this.clientService = clientService;
    this.invoiceNumberGenerator = invoiceNumberGenerator;
    this.invoicePdfWriter = invoicePdfWriter;
  }

  @Transactional
  public InvoiceResponse create(CreateInvoiceRequest request, UUID lawyerId) {
    Client client = clientService.requireOwnedClient(request.clientId(), lawyerId);
    List<TimeEntry> entries = resolveBillableEntries(request, lawyerId);
    if (entries.isEmpty()) {
      throw new NoBillableTimeException();
    }

    Invoice invoice = new Invoice();
    invoice.setLawyerId(lawyerId);
    invoice.setClientId(client.getId());
    invoice.setIssueDate(LocalDate.now(ZoneOffset.UTC));
    invoice.setDueDate(request.dueDate());
    invoice.setNotes(request.notes() == null ? null : request.notes().trim());

    BigDecimal subtotal = BigDecimal.ZERO;
    for (TimeEntry entry : entries) {
      BigDecimal amount = BillingAmounts.lineAmount(entry.getMinutes(), entry.getHourlyRate());
      InvoiceLine line = new InvoiceLine();
      line.setTimeEntryId(entry.getId());
      line.setDescription(entry.getDescription());
      line.setMinutes(entry.getMinutes());
      line.setHourlyRate(entry.getHourlyRate());
      line.setAmount(amount);
      invoice.addLine(line);
      subtotal = subtotal.add(amount);
    }
    invoice.setSubtotal(BillingAmounts.normalize(subtotal));
    invoice.setTotal(BillingAmounts.normalize(subtotal));

    Invoice saved = persistWithUniqueNumber(invoice, lawyerId);
    for (TimeEntry entry : entries) {
      entry.setInvoiceId(saved.getId());
    }

    log.info(
        "Invoice {} ({}) created for client {} with {} line(s), total {} by lawyer {}",
        saved.getId(),
        saved.getNumber(),
        client.getId(),
        entries.size(),
        saved.getTotal(),
        lawyerId);
    return InvoiceResponse.from(saved, client.getName());
  }

  @Transactional(readOnly = true)
  public Page<InvoiceSummary> list(UUID lawyerId, UUID clientId, int page, int size) {
    Page<Invoice> invoices =
        clientId == null
            ? invoiceRepository.findByLawyerIdOrderByCreatedAtDesc(
                lawyerId, PageRequests.of(page, size))
            : invoiceRepository.findByLawyerIdAndClientIdOrderByCreatedAtDesc(
                lawyerId, clientId, PageRequests.of(page, size));
    Map<UUID, String> names =
        clientNames(invoices.getContent().stream().map(Invoice::getClientId).distinct().toList());
    return invoices.map(invoice -> InvoiceSummary.from(invoice, names.get(invoice.getClientId())));
  }

  @Transactional(readOnly = true)
  public InvoiceResponse get(UUID invoiceId, UUID lawyerId) {
    Invoice invoice = requireOwnedInvoice(invoiceId, lawyerId);
    return InvoiceResponse.from(invoice, clientName(invoice.getClientId()));
  }

  @Transactional
  public InvoiceResponse updateStatus(UUID invoiceId, UUID lawyerId, InvoiceStatus target) {
    Invoice invoice = requireOwnedInvoice(invoiceId, lawyerId);
    InvoiceStatus current = invoice.getStatus();
    if (current == target) {
      return InvoiceResponse.from(invoice, clientName(invoice.getClientId()));
    }
    if (!current.canTransitionTo(target)) {
      throw new InvoiceStateException(current, target);
    }
    if (target.releasesTimeEntries()) {
      timeEntryRepository.releaseByInvoiceId(invoiceId);
    }
    invoice.setStatus(target);
    log.info("Invoice {} moved {} -> {} by lawyer {}", invoiceId, current, target, lawyerId);
    return InvoiceResponse.from(invoice, clientName(invoice.getClientId()));
  }

  @Transactional
  public void delete(UUID invoiceId, UUID lawyerId) {
    Invoice invoice = requireOwnedInvoice(invoiceId, lawyerId);
    if (invoice.getStatus() != InvoiceStatus.DRAFT) {
      throw new InvoiceStateException("Only draft invoices can be deleted; cancel it instead");
    }
    timeEntryRepository.releaseByInvoiceId(invoiceId);
    invoiceRepository.delete(invoice);
    log.info("Invoice {} deleted by lawyer {}", invoiceId, lawyerId);
  }

  @Transactional(readOnly = true)
  public ExportedFile exportPdf(UUID invoiceId, UUID lawyerId) {
    Invoice invoice = requireOwnedInvoice(invoiceId, lawyerId);
    Client client = clientRepository.findById(invoice.getClientId()).orElse(null);
    InvoiceExportModel model = toExportModel(invoice, client);

    byte[] content = invoicePdfWriter.write(model);
    String fileName =
        "Счёт_" + invoice.getNumber().replace(' ', '_') + "." + ExportFormat.PDF.extension();
    log.info(
        "Invoice {} exported as PDF by lawyer {} ({} bytes)", invoiceId, lawyerId, content.length);
    return new ExportedFile(content, fileName, ExportFormat.PDF.contentType());
  }

  private List<TimeEntry> resolveBillableEntries(CreateInvoiceRequest request, UUID lawyerId) {
    UUID clientId = request.clientId();
    if (request.timeEntryIds() != null && !request.timeEntryIds().isEmpty()) {
      return timeEntryRepository.lockBillableByIds(request.timeEntryIds(), lawyerId, clientId);
    }
    return request.caseId() == null
        ? timeEntryRepository.lockBillableForClient(clientId)
        : timeEntryRepository.lockBillableForClientAndCase(clientId, request.caseId());
  }

  private Invoice persistWithUniqueNumber(Invoice invoice, UUID lawyerId) {
    int year = invoice.getIssueDate().getYear();
    String number = invoiceNumberGenerator.next(lawyerId, year);
    for (int attempt = 0; attempt < NUMBER_RETRY_ATTEMPTS; attempt++) {
      invoice.setNumber(number);
      try {
        return invoiceRepository.saveAndFlush(invoice);
      } catch (DataIntegrityViolationException ex) {
        log.warn("Invoice number {} collided for lawyer {}, retrying", number, lawyerId);
        number = invoiceNumberGenerator.bump(number);
      }
    }
    throw new InvoiceStateException("Could not allocate a unique invoice number");
  }

  private Invoice requireOwnedInvoice(UUID invoiceId, UUID lawyerId) {
    return invoiceRepository
        .findByIdAndLawyerId(invoiceId, lawyerId)
        .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
  }

  private InvoiceExportModel toExportModel(Invoice invoice, Client client) {
    InvoiceExportModel.ClientBlock clientBlock =
        client == null
            ? new InvoiceExportModel.ClientBlock("Клиент удалён", "—", null, null, null)
            : new InvoiceExportModel.ClientBlock(
                client.getName(),
                client.getType().getDisplayName(),
                client.getInn(),
                client.getEmail(),
                client.getPhone());

    int totalMinutes = invoice.getLines().stream().mapToInt(InvoiceLine::getMinutes).sum();
    List<InvoiceExportModel.LineRow> rows =
        invoice.getLines().stream()
            .map(
                line ->
                    new InvoiceExportModel.LineRow(
                        line.getDescription(),
                        line.getMinutes(),
                        line.getHourlyRate(),
                        line.getAmount()))
            .toList();

    return new InvoiceExportModel(
        invoice.getNumber(),
        invoice.getStatus().getDisplayName(),
        invoice.getIssueDate(),
        invoice.getDueDate(),
        invoice.getCurrency(),
        clientBlock,
        rows,
        totalMinutes,
        invoice.getSubtotal(),
        invoice.getTotal(),
        invoice.getNotes());
  }

  private Map<UUID, String> clientNames(List<UUID> clientIds) {
    if (clientIds.isEmpty()) {
      return Map.of();
    }
    return clientRepository.findAllById(clientIds).stream()
        .collect(Collectors.toMap(Client::getId, Client::getName, (a, b) -> a));
  }

  private String clientName(UUID clientId) {
    return clientRepository.findById(clientId).map(Client::getName).orElse(null);
  }
}
