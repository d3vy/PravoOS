package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.shared.event.InvoicePaidKafkaPayload;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.text.DecimalFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InvoicePaidPublisher {

  private static final Logger log = LoggerFactory.getLogger(InvoicePaidPublisher.class);
  private static final String TOPIC = "invoice.paid";
  private static final DecimalFormat TOTAL_FORMATTER = new DecimalFormat("#,##0.00");

  private final ClientRepository clientRepository;
  private final OutboxEventService outboxEventService;

  public InvoicePaidPublisher(
      ClientRepository clientRepository, OutboxEventService outboxEventService) {
    this.clientRepository = clientRepository;
    this.outboxEventService = outboxEventService;
  }

  public void publish(Invoice invoice) {
    String clientName =
        clientRepository.findById(invoice.getClientId()).map(Client::getName).orElse("Клиент");
    outboxEventService.enqueue(
        TOPIC,
        invoice.getId().toString(),
        new InvoicePaidKafkaPayload(
            invoice.getId(),
            invoice.getLawyerId(),
            invoice.getNumber(),
            clientName,
            TOTAL_FORMATTER.format(invoice.getTotal()) + " " + invoice.getCurrency()));
    log.info("Enqueued invoice.paid for invoice {}", invoice.getId());
  }
}
