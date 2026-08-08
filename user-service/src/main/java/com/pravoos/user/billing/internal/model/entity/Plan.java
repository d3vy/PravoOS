package com.pravoos.user.billing.internal.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 20, unique = true)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "price_kopecks", nullable = false)
  private long priceKopecks;

  @Column(name = "daily_requests", nullable = false)
  private int dailyRequests;

  @Column(name = "daily_tokens", nullable = false)
  private long dailyTokens;

  @Column(nullable = false)
  private int seats;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    createdAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getPriceKopecks() {
    return priceKopecks;
  }

  public void setPriceKopecks(long priceKopecks) {
    this.priceKopecks = priceKopecks;
  }

  public int getDailyRequests() {
    return dailyRequests;
  }

  public void setDailyRequests(int dailyRequests) {
    this.dailyRequests = dailyRequests;
  }

  public long getDailyTokens() {
    return dailyTokens;
  }

  public void setDailyTokens(long dailyTokens) {
    this.dailyTokens = dailyTokens;
  }

  public int getSeats() {
    return seats;
  }

  public void setSeats(int seats) {
    this.seats = seats;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public void setDefault(boolean isDefault) {
    this.isDefault = isDefault;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
