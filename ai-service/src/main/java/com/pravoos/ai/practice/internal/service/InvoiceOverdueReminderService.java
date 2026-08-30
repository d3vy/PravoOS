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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceOverdueReminderService {

  private static final Logger log = LoggerFactory.getLogger(InvoiceOverdueReminderService.class);
  private static final String TOPIC = "invoice.overdue";
  private static final int[] THRESHOLDS_DAYS = {1, 3, 7, 14};
  private static final DecimalFormat TOTAL_FORMATTER = new DecimalFormat("#,##0.00");
  private static final int PAGE_SIZE = 500;

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
      Pageable pageable = PageRequest.of(0, PAGE_SIZE);
      Page<Invoice> page;
      do {
        page = invoiceRepository.findByStatusAndDueDate(InvoiceStatus.ISSUED, target, pageable);
        if (!page.isEmpty()) {
          List<Invoice> invoices = page.getContent();
          Set<UUID> alreadyReminded =
              new HashSet<>(
                  reminderRepository.findInvoiceIdByThresholdDaysAndInvoiceIdIn(
                      threshold, invoices.stream().map(Invoice::getId).toList()));
          Map<UUID, String> clientNames =
              clientRepository
                  .findAllById(invoices.stream().map(Invoice::getClientId).distinct().toList())
                  .stream()
                  .collect(Collectors.toMap(Client::getId, Client::getName, (a, b) -> a));
          for (Invoice invoice : invoices) {
            if (alreadyReminded.contains(invoice.getId())) {
              continue;
            }
            try {
              if (self.enqueueReminder(
                  invoice, threshold, clientNames.getOrDefault(invoice.getClientId(), "Клиент"))) {
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
        pageable = pageable.next();
      } while (page.hasNext());
    }
    log.info("Invoice overdue reminder scan finished, published {} reminders", published);
  }

  @Transactional
  public boolean enqueueReminder(Invoice invoice, int thresholdDays, String clientName) {
    if (reminderRepository.existsByInvoiceIdAndThresholdDays(invoice.getId(), thresholdDays)) {
      return false;
    }

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
