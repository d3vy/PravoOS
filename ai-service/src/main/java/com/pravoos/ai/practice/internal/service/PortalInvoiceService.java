package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.dto.InvoiceSummary;
import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.exception.InvoiceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalInvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final ClientRepository clientRepository;

  public PortalInvoiceService(
      InvoiceRepository invoiceRepository, ClientRepository clientRepository) {
    this.invoiceRepository = invoiceRepository;
    this.clientRepository = clientRepository;
  }

  @Transactional(readOnly = true)
  public List<InvoiceSummary> listInvoices(List<UUID> clientIds) {
    if (clientIds == null || clientIds.isEmpty()) {
      return List.of();
    }
    return invoiceRepository.findByClientIdInOrderByCreatedAtDesc(clientIds).stream()
        .map(invoice -> InvoiceSummary.from(invoice, clientName(invoice.getClientId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public InvoiceResponse getInvoice(UUID invoiceId, List<UUID> clientIds) {
    Invoice invoice = requireClientInvoice(invoiceId, clientIds);
    return InvoiceResponse.from(invoice, clientName(invoice.getClientId()));
  }

  private Invoice requireClientInvoice(UUID invoiceId, List<UUID> clientIds) {
    if (clientIds == null || clientIds.isEmpty()) {
      throw new InvoiceNotFoundException(invoiceId);
    }
    return invoiceRepository
        .findByIdAndClientIdIn(invoiceId, clientIds)
        .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
  }

  private String clientName(UUID clientId) {
    return clientRepository.findById(clientId).map(Client::getName).orElse(null);
  }
}
