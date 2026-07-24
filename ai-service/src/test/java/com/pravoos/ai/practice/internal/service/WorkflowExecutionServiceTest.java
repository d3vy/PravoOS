package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.GenerateDraftRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowRunDto;
import com.pravoos.ai.practice.internal.model.WorkflowStepConfig;
import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.practice.internal.model.entity.WorkflowRun;
import com.pravoos.ai.practice.internal.repository.jpa.WorkflowRunRepository;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import com.pravoos.ai.shared.model.enums.WorkflowRunStatus;
import com.pravoos.ai.shared.model.enums.WorkflowStepStatus;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionServiceTest {

  @Mock private WorkflowDefinitionService definitionService;
  @Mock private WorkflowRunRepository runRepository;
  @Mock private CaseService caseService;
  @Mock private DraftService draftService;
  @Mock private CaseTaskService caseTaskService;
  @Mock private LegalAiPort legalAiPort;

  private final UUID caseId = UUID.randomUUID();
  private final UUID definitionId = UUID.randomUUID();
  private final UUID lawyerId = UUID.randomUUID();

  private WorkflowExecutionService service() {
    return new WorkflowExecutionService(
        definitionService,
        runRepository,
        caseService,
        draftService,
        caseTaskService,
        legalAiPort,
        15L);
  }

  private void stubRepositoryEcho() {
    when(runRepository.save(any(WorkflowRun.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private void stubDefinition(WorkflowStepConfig... steps) {
    WorkflowDefinition definition = new WorkflowDefinition();
    definition.setName("Процесс");
    definition.setCategory(WorkflowCategory.BANKRUPTCY);
    definition.setSteps(List.of(steps));
    when(definitionService.requireVisible(eq(definitionId), eq(lawyerId), anyList()))
        .thenReturn(definition);
  }

  private AiResponseDto aiResponse(String result) {
    return new AiResponseDto(
        UUID.randomUUID(),
        caseId,
        "wf",
        "wf",
        "q",
        result,
        List.of(),
        null,
        null,
        LocalDateTime.now(),
        List.of());
  }

  @Test
  void runsAllStepsAndMarksCompleted() {
    stubDefinition(
        new WorkflowStepConfig(
            0, WorkflowStepType.AI_ANALYSIS, "Анализ", "проанализируй", null, null, null),
        new WorkflowStepConfig(
            1, WorkflowStepType.GENERATE_DRAFT, "Черновик", null, "STATEMENT", null, null),
        new WorkflowStepConfig(
            2, WorkflowStepType.GENERATE_TASKS, "Задачи", "чеклист", null, null, null),
        new WorkflowStepConfig(
            3,
            WorkflowStepType.SET_DEADLINE,
            "Срок",
            null,
            null,
            DeadlineType.FILING_DEADLINE,
            14));
    stubRepositoryEcho();
    when(legalAiPort.runCaseWorkflow(eq(caseId), eq(lawyerId), any(), any(), any()))
        .thenReturn(aiResponse("итог"));
    when(draftService.generate(
            eq(caseId), any(GenerateDraftRequest.class), eq(lawyerId), anyList()))
        .thenReturn(
            new CaseDraftDto(
                UUID.randomUUID(),
                caseId,
                "STATEMENT",
                "Исковое заявление",
                "Заявление",
                "текст",
                LocalDateTime.now(),
                null));
    when(caseTaskService.createFromChecklist(eq(caseId), any(), eq(lawyerId)))
        .thenReturn(List.of());
    when(caseService.setDeadlineIfAbsent(
            eq(caseId), eq(DeadlineType.FILING_DEADLINE), any(), eq(lawyerId), anyList()))
        .thenReturn(true);

    WorkflowRunDto run = service().run(caseId, definitionId, lawyerId, List.of());

    assertThat(run.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
    assertThat(run.steps())
        .extracting(step -> step.status())
        .containsExactly(
            WorkflowStepStatus.COMPLETED,
            WorkflowStepStatus.COMPLETED,
            WorkflowStepStatus.COMPLETED,
            WorkflowStepStatus.COMPLETED);
    assertThat(run.steps().get(0).aiResponseId()).isNotNull();
    assertThat(run.steps().get(1).draftId()).isNotNull();
  }

  @Test
  void skipsDeadlineWhenAlreadyPresent() {
    stubDefinition(
        new WorkflowStepConfig(
            0,
            WorkflowStepType.SET_DEADLINE,
            "Срок",
            null,
            null,
            DeadlineType.FILING_DEADLINE,
            30));
    stubRepositoryEcho();
    when(caseService.setDeadlineIfAbsent(
            eq(caseId), eq(DeadlineType.FILING_DEADLINE), any(), eq(lawyerId), anyList()))
        .thenReturn(false);

    WorkflowRunDto run = service().run(caseId, definitionId, lawyerId, List.of());

    assertThat(run.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
    assertThat(run.steps().get(0).status()).isEqualTo(WorkflowStepStatus.SKIPPED);
  }

  @Test
  void failedStepStopsRunAndLeavesLaterStepsPending() {
    stubDefinition(
        new WorkflowStepConfig(
            0, WorkflowStepType.AI_ANALYSIS, "Анализ", "проанализируй", null, null, null),
        new WorkflowStepConfig(
            1, WorkflowStepType.GENERATE_DRAFT, "Черновик", null, "STATEMENT", null, null),
        new WorkflowStepConfig(
            2, WorkflowStepType.SET_DEADLINE, "Срок", null, null, DeadlineType.FILING_DEADLINE, 7));
    stubRepositoryEcho();
    when(legalAiPort.runCaseWorkflow(eq(caseId), eq(lawyerId), any(), any(), any()))
        .thenReturn(aiResponse("итог"));
    lenient()
        .when(
            draftService.generate(
                eq(caseId), any(GenerateDraftRequest.class), eq(lawyerId), anyList()))
        .thenThrow(new IllegalStateException("LLM недоступен"));

    WorkflowRunDto run = service().run(caseId, definitionId, lawyerId, List.of());

    assertThat(run.status()).isEqualTo(WorkflowRunStatus.FAILED);
    assertThat(run.steps().get(0).status()).isEqualTo(WorkflowStepStatus.COMPLETED);
    assertThat(run.steps().get(1).status()).isEqualTo(WorkflowStepStatus.FAILED);
    assertThat(run.steps().get(1).error()).contains("LLM недоступен");
    assertThat(run.steps().get(2).status()).isEqualTo(WorkflowStepStatus.PENDING);
  }

  @Test
  void failStuckRunsMarksStaleRunningAsFailed() {
    WorkflowRun stuck = new WorkflowRun();
    stuck.setStatus(WorkflowRunStatus.RUNNING);
    stuck.setStartedAt(LocalDateTime.now().minusHours(1));
    when(runRepository.findByStatusAndStartedAtBefore(eq(WorkflowRunStatus.RUNNING), any()))
        .thenReturn(List.of(stuck));

    service().failStuckRuns();

    assertThat(stuck.getStatus()).isEqualTo(WorkflowRunStatus.FAILED);
    assertThat(stuck.getFinishedAt()).isNotNull();
    verify(runRepository).saveAll(List.of(stuck));
  }

  @Test
  void failStuckRunsDoesNothingWhenNoneStuck() {
    when(runRepository.findByStatusAndStartedAtBefore(eq(WorkflowRunStatus.RUNNING), any()))
        .thenReturn(List.of());

    service().failStuckRuns();

    verify(runRepository, never()).saveAll(anyList());
  }
}
