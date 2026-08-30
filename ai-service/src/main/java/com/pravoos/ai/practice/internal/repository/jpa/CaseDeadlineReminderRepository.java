package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseDeadlineReminder;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseDeadlineReminderRepository extends JpaRepository<CaseDeadlineReminder, UUID> {

  boolean existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDays(
      UUID caseId, DeadlineType deadlineType, LocalDate deadlineDate, int thresholdDays);

  boolean existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDaysAndTaskId(
      UUID caseId,
      DeadlineType deadlineType,
      LocalDate deadlineDate,
      int thresholdDays,
      UUID taskId);

  List<UUID> findCaseIdByDeadlineTypeAndDeadlineDateAndThresholdDaysAndCaseIdIn(
      DeadlineType deadlineType, LocalDate deadlineDate, int thresholdDays, List<UUID> caseIds);

  List<UUID> findTaskIdByDeadlineTypeAndDeadlineDateAndThresholdDaysAndTaskIdIn(
      DeadlineType deadlineType, LocalDate deadlineDate, int thresholdDays, List<UUID> taskIds);
}
