package com.pravoos.ai.practice.internal.recyclebin;

import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.practice.internal.repository.jpa.TimeEntryRepository;
import com.pravoos.ai.recyclebin.api.BinContents;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.api.SoftDeleteStore;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InvoiceSoftDeleteStore implements SoftDeleteStore {

  private final InvoiceRepository invoiceRepository;
  private final TimeEntryRepository timeEntryRepository;

  public InvoiceSoftDeleteStore(
      InvoiceRepository invoiceRepository, TimeEntryRepository timeEntryRepository) {
    this.invoiceRepository = invoiceRepository;
    this.timeEntryRepository = timeEntryRepository;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.INVOICE;
  }

  @Override
  public BinContents moveToBin(String entityId, DeletionActor actor, boolean cascade) {
    UUID invoiceId = UUID.fromString(entityId);
    Invoice invoice =
        invoiceRepository
            .findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
    BinContents contents = BinContents.of(snapshot(invoice));
    invoiceRepository.softDelete(invoiceId, LocalDateTime.now(ZoneOffset.UTC));
    return contents;
  }

  @Override
  public void restore(String entityId) {
    invoiceRepository.restore(UUID.fromString(entityId));
  }

  @Override
  public void purge(String entityId) {
    UUID invoiceId = UUID.fromString(entityId);
    timeEntryRepository.releaseByInvoiceId(invoiceId);
    invoiceRepository.hardDelete(invoiceId);
  }

  private static BinSnapshot snapshot(Invoice invoice) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("status", String.valueOf(invoice.getStatus()));
    payload.put("total", String.valueOf(invoice.getTotal()));
    payload.put("currency", invoice.getCurrency());
    payload.put("clientId", invoice.getClientId().toString());
    payload.put("issueDate", String.valueOf(invoice.getIssueDate()));
    return new BinSnapshot(
        RecycleBinEntityType.INVOICE,
        invoice.getId().toString(),
        invoice.getNumber(),
        invoice.getLawyerId(),
        null,
        payload);
  }
}
