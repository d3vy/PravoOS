package com.pravoos.ai.practice.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.RunWorkflowRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowInfo;
import com.pravoos.ai.shared.exception.WorkflowNotFoundException;
import com.pravoos.ai.shared.model.enums.BankruptcyWorkflow;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

  @Mock private CaseService caseService;
  @Mock private LegalAiPort legalAiPort;

  @InjectMocks private WorkflowService workflowService;

  @Test
  void listWorkflowsReturnsAllBankruptcyWorkflows() {
    List<WorkflowInfo> workflows = workflowService.listWorkflows();

    assertThat(workflows).hasSize(BankruptcyWorkflow.values().length);
    assertThat(workflows.stream().map(WorkflowInfo::id))
        .contains(BankruptcyWorkflow.CASE_SUMMARY.name());
  }

  @Test
  void runChecksQuotaAndVisibilityThenDelegatesWithWorkflowInstruction() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    List<UUID> orgIds = List.of(UUID.randomUUID());
    AiResponseDto expectedResponse =
        new AiResponseDto(
            UUID.randomUUID(),
            caseId,
            "CASE_SUMMARY",
            "Резюме дела",
            "Резюме дела",
            "Результат",
            List.of(),
            null,
            null,
            null,
            List.of());
    when(legalAiPort.runCaseWorkflow(
            eq(caseId), eq(lawyerId), any(), eq("CASE_SUMMARY"), any(), any()))
        .thenReturn(expectedResponse);

    AiResponseDto result =
        workflowService.run(caseId, "CASE_SUMMARY", new RunWorkflowRequest(null), lawyerId, orgIds);

    verify(legalAiPort).assertWithinQuota(lawyerId);
    verify(caseService).requireVisibleCase(caseId, lawyerId, orgIds);
    assertThat(result).isEqualTo(expectedResponse);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(legalAiPort)
        .runCaseWorkflow(
            eq(caseId), eq(lawyerId), any(), eq("CASE_SUMMARY"), queryCaptor.capture(), any());
    assertThat(queryCaptor.getValue()).isEqualTo(BankruptcyWorkflow.CASE_SUMMARY.displayName());
  }

  @Test
  void runUsesLawyerQuestionAsQueryAndAppendsItToInstruction() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();
    List<UUID> orgIds = List.of();
    when(legalAiPort.runCaseWorkflow(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new AiResponseDto(
                UUID.randomUUID(),
                caseId,
                "CASE_SUMMARY",
                "Резюме дела",
                "q",
                "r",
                List.of(),
                null,
                null,
                null,
                List.of()));

    workflowService.run(
        caseId, "CASE_SUMMARY", new RunWorkflowRequest(" Есть ли риски? "), lawyerId, orgIds);

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> instructionCaptor = ArgumentCaptor.forClass(String.class);
    verify(legalAiPort)
        .runCaseWorkflow(
            eq(caseId),
            eq(lawyerId),
            any(),
            eq("CASE_SUMMARY"),
            queryCaptor.capture(),
            instructionCaptor.capture());

    assertThat(queryCaptor.getValue()).isEqualTo("Есть ли риски?");
    assertThat(instructionCaptor.getValue())
        .contains(BankruptcyWorkflow.CASE_SUMMARY.instruction())
        .contains("Дополнительный вопрос юриста: Есть ли риски?");
  }

  @Test
  void runThrowsWhenWorkflowIdUnknown() {
    UUID caseId = UUID.randomUUID();
    UUID lawyerId = UUID.randomUUID();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                workflowService.run(
                    caseId, "NOT_A_WORKFLOW", new RunWorkflowRequest(null), lawyerId, List.of()))
        .isInstanceOf(WorkflowNotFoundException.class);
  }
}
