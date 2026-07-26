package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.CaseContext;
import com.pravoos.ai.core.api.CaseContextProvider;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseContextProviderImpl implements CaseContextProvider {

  private final CaseService caseService;
  private final CaseHearingEventRepository hearingEventRepository;
  private final CasePartyRepository casePartyRepository;
  private final CaseTaskRepository caseTaskRepository;

  public CaseContextProviderImpl(
      CaseService caseService,
      CaseHearingEventRepository hearingEventRepository,
      CasePartyRepository casePartyRepository,
      CaseTaskRepository caseTaskRepository) {
    this.caseService = caseService;
    this.hearingEventRepository = hearingEventRepository;
    this.casePartyRepository = casePartyRepository;
    this.caseTaskRepository = caseTaskRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public CaseContext loadContext(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
    Case caseEntity = caseService.requireVisibleCase(caseId, lawyerId, orgIds);
    return new CaseContext(
        CaseContextFormatter.formatCaseCard(caseEntity, casePartyRepository.findByCaseId(caseId)),
        CaseContextFormatter.formatTimeline(
            hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId)),
        CaseContextFormatter.formatChecklist(
            caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)));
  }
}
