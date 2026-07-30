package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.Invoice;
import com.pravoos.ai.practice.internal.model.entity.LawyerDigestSent;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.practice.internal.repository.jpa.InvoiceRepository;
import com.pravoos.ai.practice.internal.repository.jpa.LawyerDigestSentRepository;
import com.pravoos.ai.shared.client.UserServiceClient;
import com.pravoos.ai.shared.event.LawyerDigestKafkaPayload;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.InvoiceStatus;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MorningDigestService {

  private static final Logger log = LoggerFactory.getLogger(MorningDigestService.class);
  private static final String TOPIC = "lawyer.digest.morning";
  private static final int DEADLINE_HORIZON_DAYS = 7;
  private static final DecimalFormat TOTAL_FORMATTER = new DecimalFormat("#,##0.00");
  private static final Set<CaseStatus> CLOSED_STATUSES =
      EnumSet.of(CaseStatus.CLOSED_WON, CaseStatus.CLOSED_LOST);

  private final CaseRepository caseRepository;
  private final CaseTaskRepository caseTaskRepository;
  private final InvoiceRepository invoiceRepository;
  private final LawyerDigestSentRepository digestSentRepository;
  private final UserServiceClient userServiceClient;
  private final OutboxEventService outboxEventService;
  private final MorningDigestService self;

  public MorningDigestService(
      CaseRepository caseRepository,
      CaseTaskRepository caseTaskRepository,
      InvoiceRepository invoiceRepository,
      LawyerDigestSentRepository digestSentRepository,
      UserServiceClient userServiceClient,
      OutboxEventService outboxEventService,
      @Lazy MorningDigestService self) {
    this.caseRepository = caseRepository;
    this.caseTaskRepository = caseTaskRepository;
    this.invoiceRepository = invoiceRepository;
    this.digestSentRepository = digestSentRepository;
    this.userServiceClient = userServiceClient;
    this.outboxEventService = outboxEventService;
    this.self = self;
  }

  @Scheduled(cron = "${lawyer.digest.morning.cron:0 0 8 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "MorningDigestService_sendMorningDigests",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT30M")
  public void sendMorningDigests() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    log.info("Running morning digest scan for {}", today);
    List<UUID> candidateLawyerIds = collectCandidateLawyerIds(today);
    if (candidateLawyerIds.isEmpty()) {
      log.info("Morning digest scan finished, no candidate lawyers found");
      return;
    }

    List<UUID> digestEnabledLawyerIds =
        userServiceClient.filterDigestEnabledLawyerIds(candidateLawyerIds);
    int published = 0;
    for (UUID lawyerId : digestEnabledLawyerIds) {
      try {
        if (self.enqueueDigest(lawyerId, today)) {
          published++;
        }
      } catch (Exception e) {
        log.error(
            "Failed to enqueue morning digest for lawyer {}: {}", lawyerId, e.getMessage(), e);
      }
    }
    log.info("Morning digest scan finished, published {} digests", published);
  }

  private List<UUID> collectCandidateLawyerIds(LocalDate today) {
    LocalDate horizon = today.plusDays(DEADLINE_HORIZON_DAYS);
    Set<UUID> lawyerIds = new LinkedHashSet<>();
    lawyerIds.addAll(
        caseTaskRepository.findDistinctLawyerIdsWithTasksDueTodayOrOverdue(CLOSED_STATUSES, today));
    lawyerIds.addAll(
        caseRepository.findDistinctLawyerIdsWithUpcomingDeadlines(CLOSED_STATUSES, today, horizon));
    lawyerIds.addAll(invoiceRepository.findDistinctLawyerIdsByStatus(InvoiceStatus.ISSUED));
    return List.copyOf(lawyerIds);
  }

  @Transactional
  public boolean enqueueDigest(UUID lawyerId, LocalDate today) {
    if (digestSentRepository.existsByLawyerIdAndDigestDate(lawyerId, today)) {
      return false;
    }

    LocalDate horizon = today.plusDays(DEADLINE_HORIZON_DAYS);
    long tasksTodayCount =
        caseTaskRepository.countDueTodayOrOverdueByLawyerId(lawyerId, CLOSED_STATUSES, today);
    long upcomingDeadlinesCount = countUpcomingDeadlines(lawyerId, today, horizon);
    List<Invoice> unpaidInvoices =
        invoiceRepository.findByLawyerIdAndStatusOrderByDueDateAsc(lawyerId, InvoiceStatus.ISSUED);
    BigDecimal unpaidTotal =
        unpaidInvoices.stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

    if (tasksTodayCount == 0 && upcomingDeadlinesCount == 0 && unpaidInvoices.isEmpty()) {
      return false;
    }

    LawyerDigestKafkaPayload payload =
        new LawyerDigestKafkaPayload(
            lawyerId,
            today.toString(),
            (int) tasksTodayCount,
            (int) upcomingDeadlinesCount,
            unpaidInvoices.size(),
            TOTAL_FORMATTER.format(unpaidTotal));

    digestSentRepository.save(
        new LawyerDigestSent(lawyerId, today, LocalDateTime.now(ZoneOffset.UTC)));
    outboxEventService.enqueue(TOPIC, lawyerId.toString(), payload);
    log.info(
        "Enqueued morning digest: lawyer={} tasksToday={} upcomingDeadlines={} unpaidInvoices={}",
        lawyerId,
        tasksTodayCount,
        upcomingDeadlinesCount,
        unpaidInvoices.size());
    return true;
  }

  private long countUpcomingDeadlines(UUID lawyerId, LocalDate today, LocalDate horizon) {
    long count = 0;
    for (Case caseEntity :
        caseRepository.findCasesWithUpcomingDeadlines(lawyerId, CLOSED_STATUSES, today, horizon)) {
      count += isWithinHorizon(caseEntity.getFilingDeadline(), today, horizon) ? 1 : 0;
      count += isWithinHorizon(caseEntity.getNextHearingDate(), today, horizon) ? 1 : 0;
      count += isWithinHorizon(caseEntity.getExpiresAt(), today, horizon) ? 1 : 0;
    }
    count +=
        caseTaskRepository.findUpcomingByLawyerId(lawyerId, CLOSED_STATUSES, today, horizon).size();
    return count;
  }

  private boolean isWithinHorizon(LocalDate date, LocalDate today, LocalDate horizon) {
    return date != null && !date.isBefore(today) && !date.isAfter(horizon);
  }
}
