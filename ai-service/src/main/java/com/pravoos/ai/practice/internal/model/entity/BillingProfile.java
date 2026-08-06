package com.pravoos.ai.practice.internal.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "billing_profiles")
public class BillingProfile {

  @Id
  @Column(name = "lawyer_id")
  private UUID lawyerId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String name;

  @Column(length = 20)
  private String inn;

  @Column(length = 20)
  private String kpp;

  @Column(length = 20)
  private String ogrn;

  @Column(name = "legal_address", columnDefinition = "TEXT")
  private String legalAddress;

  @Column(name = "bank_name", columnDefinition = "TEXT")
  private String bankName;

  @Column(name = "bank_bic", length = 20)
  private String bankBic;

  @Column(name = "bank_account", length = 34)
  private String bankAccount;

  @Column(name = "corr_account", length = 34)
  private String corrAccount;

  @Column(columnDefinition = "TEXT")
  private String email;

  @Column(columnDefinition = "TEXT")
  private String phone;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public UUID getLawyerId() {
    return lawyerId;
  }

  public void setLawyerId(UUID lawyerId) {
    this.lawyerId = lawyerId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getInn() {
    return inn;
  }

  public void setInn(String inn) {
    this.inn = inn;
  }

  public String getKpp() {
    return kpp;
  }

  public void setKpp(String kpp) {
    this.kpp = kpp;
  }

  public String getOgrn() {
    return ogrn;
  }

  public void setOgrn(String ogrn) {
    this.ogrn = ogrn;
  }

  public String getLegalAddress() {
    return legalAddress;
  }

  public void setLegalAddress(String legalAddress) {
    this.legalAddress = legalAddress;
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getBankBic() {
    return bankBic;
  }

  public void setBankBic(String bankBic) {
    this.bankBic = bankBic;
  }

  public String getBankAccount() {
    return bankAccount;
  }

  public void setBankAccount(String bankAccount) {
    this.bankAccount = bankAccount;
  }

  public String getCorrAccount() {
    return corrAccount;
  }

  public void setCorrAccount(String corrAccount) {
    this.corrAccount = corrAccount;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
