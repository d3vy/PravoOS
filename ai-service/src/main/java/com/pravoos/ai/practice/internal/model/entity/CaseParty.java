package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "case_parties")
public class CaseParty {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "case_id", nullable = false)
  private UUID caseId;

  @Column(nullable = false, length = 500)
  private String name;

  @Column(length = 200)
  private String role;

  protected CaseParty() {}

  public CaseParty(UUID caseId, String name, String role) {
    this.caseId = caseId;
    this.name = name;
    this.role = role;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCaseId() {
    return caseId;
  }

  public String getName() {
    return name;
  }

  public String getRole() {
    return role;
  }
}
