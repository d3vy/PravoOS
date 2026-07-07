package com.pravoos.ai.core.internal;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.AiResponseQuery;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.core.internal.service.AiResponseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AiResponseQueryImpl implements AiResponseQuery {

    private final AiResponseService aiResponseService;
    private final AiResponseRepository aiResponseRepository;

    public AiResponseQueryImpl(AiResponseService aiResponseService, AiResponseRepository aiResponseRepository) {
        this.aiResponseService = aiResponseService;
        this.aiResponseRepository = aiResponseRepository;
    }

    @Override
    public List<AiResponseDto> listVisibleByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        return aiResponseService.findByCase(caseId, lawyerId, orgIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiResponseDto> listByCase(UUID caseId) {
        return aiResponseRepository.findByCaseIdOrderByCreatedAtDesc(caseId)
                .stream()
                .map(AiResponseDto::from)
                .toList();
    }
}
