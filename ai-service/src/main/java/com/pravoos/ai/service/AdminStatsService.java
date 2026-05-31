package com.pravoos.ai.service;

import com.pravoos.ai.model.dto.AiResponseDto;
import com.pravoos.ai.model.dto.AiStatsResponse;
import com.pravoos.ai.model.dto.WorkflowStat;
import com.pravoos.ai.model.enums.BankruptcyWorkflow;
import com.pravoos.ai.repository.jpa.AiResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminStatsService {

    private final AiResponseRepository aiResponseRepository;

    public AdminStatsService(AiResponseRepository aiResponseRepository) {
        this.aiResponseRepository = aiResponseRepository;
    }

    @Transactional(readOnly = true)
    public AiStatsResponse getStats() {
        long total = aiResponseRepository.count();
        long rated = aiResponseRepository.countRated();
        long positive = aiResponseRepository.countPositive();
        long negative = aiResponseRepository.countNegative();

        List<WorkflowStat> workflows = aiResponseRepository.aggregateByWorkflow()
                .stream()
                .map(this::toWorkflowStat)
                .toList();

        return new AiStatsResponse(total, rated, positive, negative, workflows);
    }

    @Transactional(readOnly = true)
    public List<AiResponseDto> getRecentResponses() {
        return aiResponseRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(AiResponseDto::from)
                .toList();
    }

    private WorkflowStat toWorkflowStat(Object[] row) {
        String workflowId = (String) row[0];
        long count = ((Number) row[1]).longValue();
        Double avgRating = row[2] != null ? ((Number) row[2]).doubleValue() : null;
        return new WorkflowStat(workflowId, resolveName(workflowId), count, avgRating);
    }

    private String resolveName(String workflowId) {
        try {
            return BankruptcyWorkflow.valueOf(workflowId).displayName();
        } catch (IllegalArgumentException ex) {
            return workflowId;
        }
    }
}
