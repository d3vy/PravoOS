package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.CaseStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "cases")
public class Case {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID lawyerId;

  @Column(name = "org_id")
  private UUID orgId;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "client_id")
  private UUID clientId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CaseStatus status = CaseStatus.INTAKE;

  @Column(name = "filing_deadline")
  private LocalDate filingDeadline;

  @Column(name = "next_hearing_date")
  private LocalDate nextHearingDate;

  @Column(name = "expires_at")
  private LocalDate expiresAt;

  @Column(name = "arbitr_case_number", length = 50)
  private String arbitrCaseNumber;

  @Column(name = "arbitr_case_guid", length = 40)
  private String arbitrCaseGuid;

  @Column(name = "arbitr_judge", length = 300)
  private String arbitrJudge;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() {
    return id;
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public void setLawyerId(UUID lawyerId) {
    this.lawyerId = lawyerId;
  }

  public UUID getOrgId() {
    return orgId;
  }

  public void setOrgId(UUID orgId) {
    this.orgId = orgId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public UUID getClientId() {
    return clientId;
  }

  public void setClientId(UUID clientId) {
    this.clientId = clientId;
  }

  public CaseStatus getStatus() {
    return status;
  }

  public void setStatus(CaseStatus status) {
    this.status = status;
  }

  public LocalDate getFilingDeadline() {
    return filingDeadline;
  }

  public void setFilingDeadline(LocalDate filingDeadline) {
    this.filingDeadline = filingDeadline;
  }

  public LocalDate getNextHearingDate() {
    return nextHearingDate;
  }

  public void setNextHearingDate(LocalDate nextHearingDate) {
    this.nextHearingDate = nextHearingDate;
  }

  public LocalDate getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDate expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getArbitrCaseNumber() {
    return arbitrCaseNumber;
  }

  public void setArbitrCaseNumber(String arbitrCaseNumber) {
    this.arbitrCaseNumber = arbitrCaseNumber;
  }

  public String getArbitrCaseGuid() {
    return arbitrCaseGuid;
  }

  public void setArbitrCaseGuid(String arbitrCaseGuid) {
    this.arbitrCaseGuid = arbitrCaseGuid;
  }

  public String getArbitrJudge() {
    return arbitrJudge;
  }

  public void setArbitrJudge(String arbitrJudge) {
    this.arbitrJudge = arbitrJudge;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
