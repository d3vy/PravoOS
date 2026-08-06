package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.CaseContext;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.model.entity.CaseHearingEvent;
import com.pravoos.ai.practice.internal.model.entity.CaseParty;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.practice.internal.repository.jpa.CaseHearingEventRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CasePartyRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseContextProviderImplTest {

  @Mock private CaseService caseService;
  @Mock private CaseHearingEventRepository hearingEventRepository;
  @Mock private CasePartyRepository casePartyRepository;
  @Mock private CaseTaskRepository caseTaskRepository;

  @InjectMocks private CaseContextProviderImpl caseContextProvider;

  @Test
  void loadContextChecksVisibilityAndAssemblesFormattedContext() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    List<UUID> orgIds = List.of(UUID.randomUUID());

    Case caseEntity = new Case();
    caseEntity.setTitle("Дело №1");
    caseEntity.setStatus(CaseStatus.IN_PROGRESS);
    when(caseService.requireVisibleCase(caseId, lawyerId, orgIds)).thenReturn(caseEntity);

    CaseParty party = new CaseParty(caseId, "Истец", null);
    when(casePartyRepository.findByCaseId(caseId)).thenReturn(List.of(party));

    when(hearingEventRepository.findByCaseIdOrderByEventDateDescCreatedAtDesc(caseId))
        .thenReturn(List.<CaseHearingEvent>of());

    CaseTask task = new CaseTask();
    task.setText("Задача");
    when(caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId))
        .thenReturn(List.of(task));

    CaseContext context = caseContextProvider.loadContext(caseId, lawyerId, orgIds);

    assertThat(context.caseCard()).contains("Дело №1").contains("Истец");
    assertThat(context.hearingTimeline()).isEqualTo("Событий по делу из КАД.Арбитр пока нет.");
    assertThat(context.checklist()).contains("Задача");
  }
}
