package com.pravoos.ai.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CaseExportModel(
        String title,
        String description,
        String status,
        LocalDateTime createdAt,
        ClientSection client,
        List<DocumentSection> documents,
        List<ResponseSection> responses,
        List<DraftSection> drafts
) {
    public record ClientSection(
            String name,
            String type,
            String phone,
            String email,
            String inn,
            String notes
    ) {}

    public record DocumentSection(
            String title,
            String fileName,
            String status,
            LocalDateTime uploadedAt
    ) {}

    public record ResponseSection(
            String workflowName,
            String query,
            String result,
            List<String> sources,
            LocalDateTime createdAt
    ) {}

    public record DraftSection(
            String typeName,
            String title,
            String content,
            LocalDateTime createdAt
    ) {}
}
