package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.internal.dto.RateRequest;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.shared.exception.AiResponseNotFoundException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiResponseService {

  private static final Logger log = LoggerFactory.getLogger(AiResponseService.class);

  private final AiResponseRepository aiResponseRepository;
  private final CaseAccessProvider caseAccessProvider;

  public AiResponseService(
      AiResponseRepository aiResponseRepository, CaseAccessProvider caseAccessProvider) {
    this.aiResponseRepository = aiResponseRepository;
    this.caseAccessProvider = caseAccessProvider;
  }

  @Transactional(readOnly = true)
  public List<AiResponseDto> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    caseAccessProvider.assertCaseVisible(caseId, lawyerId, orgIds);
    return aiResponseRepository.findByCaseIdOrderByCreatedAtDesc(caseId).stream()
        .map(AiResponseDto::from)
        .toList();
  }

  @Transactional
  public AiResponseDto rate(
      UUID responseId, RateRequest request, UUID lawyerId, List<UUID> orgIds) {
    AiResponse response =
        aiResponseRepository
            .findById(responseId)
            .orElseThrow(() -> new AiResponseNotFoundException(responseId));
    caseAccessProvider.assertCaseVisible(response.getCaseId(), lawyerId, orgIds);

    response.setRating(request.rating().shortValue());
    response.setRatingComment(request.comment());
    AiResponse saved = aiResponseRepository.save(response);
    log.info("Response {} rated {} by lawyer {}", responseId, request.rating(), lawyerId);
    return AiResponseDto.from(saved);
  }
}
