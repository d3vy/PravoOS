package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.practice.internal.model.WorkflowStepConfig;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinition {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id")
  private UUID orgId;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private WorkflowCategory category;

  @Column(name = "is_system", nullable = false)
  private boolean system;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<WorkflowStepConfig> steps;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    createdAt = now;
    updatedAt = now;
    if (steps == null) {
      steps = List.of();
    }
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public void setOrgId(UUID orgId) {
    this.orgId = orgId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public WorkflowCategory getCategory() {
    return category;
  }

  public void setCategory(WorkflowCategory category) {
    this.category = category;
  }

  public boolean isSystem() {
    return system;
  }

  public void setSystem(boolean system) {
    this.system = system;
  }

  public List<WorkflowStepConfig> getSteps() {
    return steps;
  }

  public void setSteps(List<WorkflowStepConfig> steps) {
    this.steps = steps;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
