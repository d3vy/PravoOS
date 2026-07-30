package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseDeadlineReminder;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDeadlineReminderRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.event.CaseDeadlineKafkaPayload;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import com.pravoos.ai.shared.service.OutboxEventService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadlineReminderService {

  private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);
  private static final String TOPIC = "case.deadline.approaching";
  private static final int[] THRESHOLDS_DAYS = {7, 3, 1};
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final CaseRepository caseRepository;
  private final CaseTaskRepository caseTaskRepository;
  private final CaseDeadlineReminderRepository reminderRepository;
  private final OutboxEventService outboxEventService;
  private final DeadlineReminderService self;

  public DeadlineReminderService(
      CaseRepository caseRepository,
      CaseTaskRepository caseTaskRepository,
      CaseDeadlineReminderRepository reminderRepository,
      OutboxEventService outboxEventService,
      @Lazy DeadlineReminderService self) {
    this.caseRepository = caseRepository;
    this.caseTaskRepository = caseTaskRepository;
    this.reminderRepository = reminderRepository;
    this.outboxEventService = outboxEventService;
    this.self = self;
  }

  @Scheduled(cron = "${deadline.reminder.cron:0 0 9 * * *}", zone = "UTC")
  @SchedulerLock(
      name = "DeadlineReminderService_sendDueReminders",
      lockAtLeastFor = "PT1M",
      lockAtMostFor = "PT30M")
  public void sendDueReminders() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    log.info("Running deadline reminder scan for {}", today);
    int published = 0;
    for (int threshold : THRESHOLDS_DAYS) {
      LocalDate target = today.plusDays(threshold);
      published +=
          processType(DeadlineType.FILING_DEADLINE, Case::getFilingDeadline, target, threshold);
      published +=
          processType(DeadlineType.NEXT_HEARING, Case::getNextHearingDate, target, threshold);
      published += processType(DeadlineType.EXPIRY, Case::getExpiresAt, target, threshold);
      published += processTaskReminders(target, threshold);
    }
    log.info("Deadline reminder scan finished, published {} reminders", published);
  }

  private int processType(
      DeadlineType type, Function<Case, LocalDate> dateAccessor, LocalDate target, int threshold) {
    List<Case> cases = casesForType(type, target);
    int published = 0;
    for (Case caseEntity : cases) {
      try {
        if (self.enqueueReminder(caseEntity, type, dateAccessor.apply(caseEntity), threshold)) {
          published++;
        }
      } catch (Exception e) {
        log.error(
            "Failed to enqueue deadline reminder for case {} type {}: {}",
            caseEntity.getId(),
            type,
            e.getMessage(),
            e);
      }
    }
    return published;
  }

  private List<Case> casesForType(DeadlineType type, LocalDate target) {
    return switch (type) {
      case FILING_DEADLINE -> caseRepository.findByFilingDeadline(target);
      case NEXT_HEARING -> caseRepository.findByNextHearingDate(target);
      case EXPIRY -> caseRepository.findByExpiresAt(target);
      case TASK -> List.of();
    };
  }

  private int processTaskReminders(LocalDate target, int threshold) {
    int published = 0;
    for (CaseTaskRepository.TaskReminderView task : caseTaskRepository.findDueOnDate(target)) {
      try {
        if (self.enqueueTaskReminder(task, threshold)) {
          published++;
        }
      } catch (Exception e) {
        log.error(
            "Failed to enqueue task reminder for task {} on case {}: {}",
            task.getId(),
            task.getCaseId(),
            e.getMessage(),
            e);
      }
    }
    return published;
  }

  @Transactional
  public boolean enqueueTaskReminder(CaseTaskRepository.TaskReminderView task, int threshold) {
    if (reminderRepository.existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDaysAndTaskId(
        task.getCaseId(), DeadlineType.TASK, task.getDueDate(), threshold, task.getId())) {
      return false;
    }

    CaseDeadlineKafkaPayload payload =
        new CaseDeadlineKafkaPayload(
            task.getCaseId(),
            task.getLawyerId(),
            task.getCaseTitle(),
            task.getText(),
            DATE_FORMATTER.format(task.getDueDate()),
            threshold);

    reminderRepository.save(
        new CaseDeadlineReminder(
            task.getCaseId(),
            DeadlineType.TASK,
            task.getDueDate(),
            threshold,
            LocalDateTime.now(ZoneOffset.UTC),
            task.getId()));
    outboxEventService.enqueue(TOPIC, task.getCaseId().toString(), payload);
    log.info(
        "Enqueued task reminder: task={} case={} date={} daysLeft={}",
        task.getId(),
        task.getCaseId(),
        task.getDueDate(),
        threshold);
    return true;
  }

  @Transactional
  public boolean enqueueReminder(
      Case caseEntity, DeadlineType type, LocalDate deadlineDate, int threshold) {
    if (reminderRepository.existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDays(
        caseEntity.getId(), type, deadlineDate, threshold)) {
      return false;
    }

    CaseDeadlineKafkaPayload payload =
        new CaseDeadlineKafkaPayload(
            caseEntity.getId(),
            caseEntity.getLawyerId(),
            caseEntity.getTitle(),
            type.getDisplayName(),
            DATE_FORMATTER.format(deadlineDate),
            threshold);

    reminderRepository.save(
        new CaseDeadlineReminder(
            caseEntity.getId(), type, deadlineDate, threshold, LocalDateTime.now(ZoneOffset.UTC)));
    outboxEventService.enqueue(TOPIC, caseEntity.getId().toString(), payload);
    log.info(
        "Enqueued deadline reminder: case={} type={} date={} daysLeft={}",
        caseEntity.getId(),
        type,
        deadlineDate,
        threshold);
    return true;
  }
}
