package com.pravoos.ai.model.dto;

import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.model.enums.BankruptcyWorkflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AiResponseDto(
        UUID id,
        UUID caseId,
        String workflowId,
        String workflowName,
        String query,
        String result,
        List<SourceReference> sources,
        Short rating,
        String ratingComment,
        LocalDateTime createdAt,
        List<String> followUps
) {
    public static AiResponseDto from(AiResponse response) {
        return from(response, List.of());
    }

    public static AiResponseDto from(AiResponse response, List<String> followUps) {
        String workflowName = resolveWorkflowName(response.getWorkflowId());
        return new AiResponseDto(
                response.getId(),
                response.getCaseId(),
                response.getWorkflowId(),
                workflowName,
                response.getQuery(),
                response.getResult(),
                response.getSources() != null ? response.getSources() : List.of(),
                response.getRating(),
                response.getRatingComment(),
                response.getCreatedAt(),
                followUps
        );
    }

    private static String resolveWorkflowName(String workflowId) {
        try {
            return BankruptcyWorkflow.valueOf(workflowId).displayName();
        } catch (IllegalArgumentException ex) {
            return workflowId;
        }
    }
}
