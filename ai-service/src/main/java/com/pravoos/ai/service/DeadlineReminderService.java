package com.pravoos.ai.service;

import com.pravoos.ai.event.CaseDeadlineKafkaPayload;
import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.entity.CaseDeadlineReminder;
import com.pravoos.ai.model.enums.DeadlineType;
import com.pravoos.ai.repository.jpa.CaseDeadlineReminderRepository;
import com.pravoos.ai.repository.jpa.CaseRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

@Service
public class DeadlineReminderService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderService.class);
    private static final String TOPIC = "case.deadline.approaching";
    private static final int[] THRESHOLDS_DAYS = {7, 3, 1};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final CaseRepository caseRepository;
    private final CaseDeadlineReminderRepository reminderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeadlineReminderService(CaseRepository caseRepository,
                                   CaseDeadlineReminderRepository reminderRepository,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        this.caseRepository = caseRepository;
        this.reminderRepository = reminderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(cron = "${deadline.reminder.cron:0 0 9 * * *}", zone = "UTC")
    @SchedulerLock(name = "DeadlineReminderService_sendDueReminders", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void sendDueReminders() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        log.info("Running deadline reminder scan for {}", today);
        int published = 0;
        for (int threshold : THRESHOLDS_DAYS) {
            LocalDate target = today.plusDays(threshold);
            published += processType(DeadlineType.FILING_DEADLINE, Case::getFilingDeadline, target, threshold);
            published += processType(DeadlineType.NEXT_HEARING, Case::getNextHearingDate, target, threshold);
            published += processType(DeadlineType.EXPIRY, Case::getExpiresAt, target, threshold);
        }
        log.info("Deadline reminder scan finished, published {} reminders", published);
    }

    private int processType(DeadlineType type, Function<Case, LocalDate> dateAccessor,
                            LocalDate target, int threshold) {
        List<Case> cases = casesForType(type, target);
        int published = 0;
        for (Case caseEntity : cases) {
            try {
                if (sendReminder(caseEntity, type, dateAccessor.apply(caseEntity), threshold)) {
                    published++;
                }
            } catch (Exception e) {
                log.error("Failed to publish deadline reminder for case {} type {}: {}",
                        caseEntity.getId(), type, e.getMessage(), e);
            }
        }
        return published;
    }

    private List<Case> casesForType(DeadlineType type, LocalDate target) {
        return switch (type) {
            case FILING_DEADLINE -> caseRepository.findByFilingDeadline(target);
            case NEXT_HEARING -> caseRepository.findByNextHearingDate(target);
            case EXPIRY -> caseRepository.findByExpiresAt(target);
        };
    }

    private boolean sendReminder(Case caseEntity, DeadlineType type, LocalDate deadlineDate, int threshold) {
        if (reminderRepository.existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDays(
                caseEntity.getId(), type, deadlineDate, threshold)) {
            return false;
        }

        CaseDeadlineKafkaPayload payload = new CaseDeadlineKafkaPayload(
                caseEntity.getId(),
                caseEntity.getLawyerId(),
                caseEntity.getTitle(),
                type.getDisplayName(),
                DATE_FORMATTER.format(deadlineDate),
                threshold);

        publish(caseEntity.getId().toString(), payload);
        reminderRepository.save(new CaseDeadlineReminder(
                caseEntity.getId(), type, deadlineDate, threshold, LocalDateTime.now(ZoneOffset.UTC)));
        log.info("Published deadline reminder: case={} type={} date={} daysLeft={}",
                caseEntity.getId(), type, deadlineDate, threshold);
        return true;
    }

    private void publish(String key, CaseDeadlineKafkaPayload payload) {
        try {
            kafkaTemplate.send(TOPIC, key, payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing deadline reminder", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish deadline reminder to Kafka", e.getCause());
        }
    }
}
