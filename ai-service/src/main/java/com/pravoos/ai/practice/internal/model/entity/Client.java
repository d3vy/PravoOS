package com.pravoos.ai.practice.internal.model.entity;

import com.pravoos.ai.shared.model.enums.ClientType;
import com.pravoos.ai.shared.security.PiiStringConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "clients")
@SQLRestriction("deleted_at IS NULL")
public class Client {

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private UUID lawyerId;

  @Convert(converter = PiiStringConverter.class)
  @Column(nullable = false, columnDefinition = "TEXT")
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ClientType type;

  @Convert(converter = PiiStringConverter.class)
  @Column(columnDefinition = "TEXT")
  private String phone;

  @Convert(converter = PiiStringConverter.class)
  @Column(columnDefinition = "TEXT")
  private String email;

  @Convert(converter = PiiStringConverter.class)
  @Column(columnDefinition = "TEXT")
  private String inn;

  @Convert(converter = PiiStringConverter.class)
  @Column(columnDefinition = "TEXT")
  private String notes;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public ClientType getType() {
    return type;
  }

  public void setType(ClientType type) {
    this.type = type;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getInn() {
    return inn;
  }

  public void setInn(String inn) {
    this.inn = inn;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
