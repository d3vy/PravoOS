package com.pravoos.ai.recyclebin.internal.model;

import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBinArea;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "deleted_entry")
public class DeletedEntry {

  private static final int TITLE_MAX_LENGTH = 512;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id")
  private UUID orgId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 64)
  private RecycleBinEntityType entityType;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(nullable = false, length = TITLE_MAX_LENGTH)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RecycleBinArea area;

  @Column(name = "deleted_by", nullable = false)
  private UUID deletedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "deleted_by_role", nullable = false, length = 32)
  private DeletionRole deletedByRole;

  @Column(name = "deleted_at", nullable = false)
  private LocalDateTime deletedAt;

  @Column(name = "purge_after", nullable = false)
  private LocalDateTime purgeAfter;

  @Column(name = "restored_at")
  private LocalDateTime restoredAt;

  @Column(name = "cascade_group_id", nullable = false)
  private UUID cascadeGroupId;

  @Column(name = "cascade_root", nullable = false)
  private boolean cascadeRoot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload = Map.of();

  public UUID getId() {
    return id;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public void setOrgId(UUID orgId) {
    this.orgId = orgId;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  public RecycleBinEntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(RecycleBinEntityType entityType) {
    this.entityType = entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title =
        title != null && title.length() > TITLE_MAX_LENGTH
            ? title.substring(0, TITLE_MAX_LENGTH)
            : title;
  }

  public RecycleBinArea getArea() {
    return area;
  }

  public void setArea(RecycleBinArea area) {
    this.area = area;
  }

  public UUID getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(UUID deletedBy) {
    this.deletedBy = deletedBy;
  }

  public DeletionRole getDeletedByRole() {
    return deletedByRole;
  }

  public void setDeletedByRole(DeletionRole deletedByRole) {
    this.deletedByRole = deletedByRole;
  }

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public LocalDateTime getPurgeAfter() {
    return purgeAfter;
  }

  public void setPurgeAfter(LocalDateTime purgeAfter) {
    this.purgeAfter = purgeAfter;
  }

  public LocalDateTime getRestoredAt() {
    return restoredAt;
  }

  public void setRestoredAt(LocalDateTime restoredAt) {
    this.restoredAt = restoredAt;
  }

  public UUID getCascadeGroupId() {
    return cascadeGroupId;
  }

  public void setCascadeGroupId(UUID cascadeGroupId) {
    this.cascadeGroupId = cascadeGroupId;
  }

  public boolean isCascadeRoot() {
    return cascadeRoot;
  }

  public void setCascadeRoot(boolean cascadeRoot) {
    this.cascadeRoot = cascadeRoot;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload == null ? Map.of() : payload;
  }
}
