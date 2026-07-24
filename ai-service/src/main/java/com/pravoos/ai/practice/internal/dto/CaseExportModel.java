package com.pravoos.ai.practice.internal.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CaseExportModel(
    String title,
    String description,
    String status,
    LocalDateTime createdAt,
    ClientSection client,
    List<DocumentSection> documents,
    List<TaskSection> tasks,
    List<ResponseSection> responses,
    List<DraftSection> drafts) {
  public record ClientSection(
      String name, String type, String phone, String email, String inn, String notes) {}

  public record DocumentSection(
      String title, String fileName, String status, LocalDateTime uploadedAt) {}

  public record TaskSection(String text, boolean done, LocalDate dueDate) {}

  public record ResponseSection(
      String workflowName,
      String query,
      String result,
      List<String> sources,
      LocalDateTime createdAt) {}

  public record DraftSection(
      String typeName, String title, String content, LocalDateTime createdAt) {}
}
