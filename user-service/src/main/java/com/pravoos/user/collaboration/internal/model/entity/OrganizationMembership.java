package com.pravoos.user.collaboration.internal.model.entity;

import com.pravoos.user.collaboration.internal.model.enums.OrgRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_memberships")
public class OrganizationMembership {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false)
  private UUID orgId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "org_role", nullable = false, length = 20)
  private OrgRole orgRole;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now();
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

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public OrgRole getOrgRole() {
    return orgRole;
  }

  public void setOrgRole(OrgRole orgRole) {
    this.orgRole = orgRole;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
