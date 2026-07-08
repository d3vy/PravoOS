package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.practice.internal.model.WorkflowStepRun;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import com.pravoos.ai.shared.model.enums.WorkflowRunStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "definition_name", nullable = false, length = 200)
    private String definitionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WorkflowCategory category;

    @Column(name = "lawyer_id", nullable = false)
    private UUID lawyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowRunStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<WorkflowStepRun> steps;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime finishedAt;

    @PrePersist
    void prePersist() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
        if (steps == null) {
            steps = List.of();
        }
    }

    public UUID getId() { return id; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public UUID getDefinitionId() { return definitionId; }
    public void setDefinitionId(UUID definitionId) { this.definitionId = definitionId; }

    public String getDefinitionName() { return definitionName; }
    public void setDefinitionName(String definitionName) { this.definitionName = definitionName; }

    public WorkflowCategory getCategory() { return category; }
    public void setCategory(WorkflowCategory category) { this.category = category; }

    public UUID getLawyerId() { return lawyerId; }
    public void setLawyerId(UUID lawyerId) { this.lawyerId = lawyerId; }

    public WorkflowRunStatus getStatus() { return status; }
    public void setStatus(WorkflowRunStatus status) { this.status = status; }

    public List<WorkflowStepRun> getSteps() { return steps; }
    public void setSteps(List<WorkflowStepRun> steps) { this.steps = steps; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
