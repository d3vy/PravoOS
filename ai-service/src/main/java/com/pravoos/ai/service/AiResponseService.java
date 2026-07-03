package com.pravoos.ai.service;

import com.pravoos.ai.exception.AiResponseNotFoundException;
import com.pravoos.ai.model.dto.AiResponseDto;
import com.pravoos.ai.model.dto.RateRequest;
import com.pravoos.ai.model.entity.AiResponse;
import com.pravoos.ai.repository.jpa.AiResponseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AiResponseService {

    private static final Logger log = LoggerFactory.getLogger(AiResponseService.class);

    private final AiResponseRepository aiResponseRepository;
    private final CaseService caseService;

    public AiResponseService(AiResponseRepository aiResponseRepository, CaseService caseService) {
        this.aiResponseRepository = aiResponseRepository;
        this.caseService = caseService;
    }

    @Transactional(readOnly = true)
    public List<AiResponseDto> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        return aiResponseRepository.findByCaseIdOrderByCreatedAtDesc(caseId)
                .stream()
                .map(AiResponseDto::from)
                .toList();
    }

    @Transactional
    public AiResponseDto rate(UUID responseId, RateRequest request, UUID lawyerId, List<UUID> orgIds) {
        AiResponse response = aiResponseRepository.findById(responseId)
                .orElseThrow(() -> new AiResponseNotFoundException(responseId));
        caseService.requireVisibleCase(response.getCaseId(), lawyerId, orgIds);

        response.setRating(request.rating().shortValue());
        response.setRatingComment(request.comment());
        AiResponse saved = aiResponseRepository.save(response);
        log.info("Response {} rated {} by lawyer {}", responseId, request.rating(), lawyerId);
        return AiResponseDto.from(saved);
    }
}
