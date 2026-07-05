package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseDeadlineReminder;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface CaseDeadlineReminderRepository extends JpaRepository<CaseDeadlineReminder, UUID> {

    boolean existsByCaseIdAndDeadlineTypeAndDeadlineDateAndThresholdDays(
            UUID caseId, DeadlineType deadlineType, LocalDate deadlineDate, int thresholdDays);
}
