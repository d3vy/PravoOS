package com.pravoos.ai.model.entity;

import com.pravoos.ai.model.enums.DeadlineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "case_deadline_reminders")
public class CaseDeadlineReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_type", nullable = false, length = 30)
    private DeadlineType deadlineType;

    @Column(name = "deadline_date", nullable = false)
    private LocalDate deadlineDate;

    @Column(name = "threshold_days", nullable = false)
    private int thresholdDays;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    protected CaseDeadlineReminder() {
    }

    public CaseDeadlineReminder(UUID caseId, DeadlineType deadlineType, LocalDate deadlineDate,
                                int thresholdDays, LocalDateTime sentAt) {
        this.caseId = caseId;
        this.deadlineType = deadlineType;
        this.deadlineDate = deadlineDate;
        this.thresholdDays = thresholdDays;
        this.sentAt = sentAt;
    }

    public UUID getId() { return id; }

    public UUID getCaseId() { return caseId; }

    public DeadlineType getDeadlineType() { return deadlineType; }

    public LocalDate getDeadlineDate() { return deadlineDate; }

    public int getThresholdDays() { return thresholdDays; }

    public LocalDateTime getSentAt() { return sentAt; }
}
