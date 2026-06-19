package com.pravoos.ai.model.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "case_hearing_events")
public class CaseHearingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "source_event_id", nullable = false, length = 200)
    private String sourceEventId;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "event_type", length = 300)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "court_name", length = 500)
    private String courtName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CaseHearingEvent() {
    }

    public CaseHearingEvent(UUID caseId, String sourceEventId, LocalDate eventDate,
                            String eventType, String description, String courtName) {
        this.caseId = caseId;
        this.sourceEventId = sourceEventId;
        this.eventDate = eventDate;
        this.eventType = eventType;
        this.description = description;
        this.courtName = courtName;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public String getSourceEventId() { return sourceEventId; }
    public LocalDate getEventDate() { return eventDate; }
    public String getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getCourtName() { return courtName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
