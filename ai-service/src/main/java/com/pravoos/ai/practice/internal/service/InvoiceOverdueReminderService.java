package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Client;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.InvoiceOverdueReminder;
import com.pravoos.ai.practice.internal.repository.jpa.ClientRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceOverdueReminderRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.shared.event.InvoiceOverdueKafkaPayload;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceOverdueReminderService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceOverdueReminderService.class);
  private static final String TOPIC = "invoice.overdue";
  private static final int[] THRESHOLDS_DAYS = {1, 3, 7, 14};
  private static final DecimalFormat TOTAL_FORMATTER = new DecimalFormat("#,##0.00");

  private final InvoiceRepository invoiceRepository;
  private final ClientRepository clientRepository;
  private final InvoiceOverdueReminderRepository reminderRepository;
  private final OutboxEventService outboxEventService;
  private final InvoiceOverdueReminderService self;

  public InvoiceOverdueReminderService(
      InvoiceRepository invoiceRepository,
      ClientRepository clientRepository,
      InvoiceOverdueReminderRepository reminderRepository,
      OutboxEventService outboxEventService,
      @Lazy InvoiceOverdueReminderService self) {
    this.invoiceRepository = invoiceRepository;
    this.clientRepository = clientRepository;
    this.reminderRepository = reminderRepository;
    this.outboxEventService = outboxEventService;
    this.self = self;
  }

  @Scheduled(cron = "${invoice.overdue.reminder.cron:0 0 9 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "InvoiceOverdueReminderService_sendOverdueReminders",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT30M")
  public void sendOverdueReminders() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    log.info("Running invoice overdue reminder scan for {}", today);
    int published = 0;
    for (int threshold : THRESHOLDS_DAYS) {
      LocalDate target = today.minusDays(threshold);
      List<Invoice> invoices =
          invoiceRepository.findByStatusAndDueDate(InvoiceStatus.ISSUED, target);
      for (Invoice invoice : invoices) {
        try {
          if (self.enqueueReminder(invoice, threshold)) {
            published++;
          }
        } catch (Exception e) {
          log.error(
              "Failed to enqueue overdue reminder for invoice {}: {}",
              invoice.getId(),
              e.getMessage(),
              e);
        }
      }
    }
    log.info("Invoice overdue reminder scan finished, published {} reminders", published);
  }

  @Transactional
  public boolean enqueueReminder(Invoice invoice, int thresholdDays) {
    if (reminderRepository.existsByInvoiceIdAndThresholdDays(invoice.getId(), thresholdDays)) {
      return false;
    }

    String clientName =
        clientRepository.findById(invoice.getClientId()).map(Client::getName).orElse("Клиент");
    InvoiceOverdueKafkaPayload payload =
        new InvoiceOverdueKafkaPayload(
            invoice.getId(),
            invoice.getLawyerId(),
            invoice.getNumber(),
            clientName,
            TOTAL_FORMATTER.format(invoice.getTotal()) + " " + invoice.getCurrency(),
            thresholdDays);

    reminderRepository.save(
        new InvoiceOverdueReminder(
            invoice.getId(), thresholdDays, LocalDateTime.now(ZoneOffset.UTC)));
    outboxEventService.enqueue(TOPIC, invoice.getId().toString(), payload);
    log.info(
        "Enqueued overdue reminder: invoice={} daysOverdue={}", invoice.getId(), thresholdDays);
    return true;
  }
}
