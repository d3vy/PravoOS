package com.pravoos.ai.practice.internal.dto;

import com.pravoos.ai.practice.internal.model.entity.CaseTask;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaseTaskResponse(
        UUID id,
        UUID caseId,
        String text,
        LocalDate dueDate,
        boolean done,
        LocalDateTime createdAt
) {
    public static CaseTaskResponse from(CaseTask task) {
        return new CaseTaskResponse(
                task.getId(),
                task.getCaseId(),
                task.getText(),
                task.getDueDate(),
                task.isDone(),
                task.getCreatedAt()
        );
    }
}
