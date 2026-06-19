package com.pravoos.ai.model.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "case_tasks")
public class CaseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
