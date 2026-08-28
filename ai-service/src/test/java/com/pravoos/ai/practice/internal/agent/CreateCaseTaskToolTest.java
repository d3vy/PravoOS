package com.pravoos.ai.practice.internal.agent;

import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ADMIN;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.MAPPER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ORG_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.practice.internal.dto.CaseTaskResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseTaskRequest;
import com.pravoos.ai.practice.internal.service.CaseTaskService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateCaseTaskToolTest {

  private static final UUID CASE_ID = UUID.randomUUID();

  @Mock private AiActionProposals proposals;
  @Mock private CaseTaskService caseTaskService;

  private CreateCaseTaskTool tool() {
    return new CreateCaseTaskTool(proposals, VALIDATOR, caseTaskService, MAPPER);
  }

  @Test
  void isAWriteToolOfferedToLawyersOnly() {
    assertThat(tool().readOnly()).isFalse();
    assertThat(tool().requiresApproval()).isTrue();
    assertThat(tool().availableFor(LAWYER)).isTrue();
    assertThat(tool().availableFor(ADMIN)).isFalse();
  }

  @Test
  void executeOnlyProposesAndNeverCreatesTheTask() {
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    when(proposals.propose(any(), any(), title.capture(), any()))
        .thenReturn(
            new ProposedAction(
                UUID.randomUUID(), "create_task", "t", LocalDateTime.now().plusMinutes(30)));

    AiToolResult result =
        tool().execute(arguments("Подготовить отзыв").put("dueDate", "2026-09-01"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(title.getValue()).contains("Подготовить отзыв").contains("2026-09-01");
    verify(caseTaskService, never()).create(any(), any(), any(), any());
  }

  @Test
  void describesATaskWithoutADueDateWithoutAnEmptyParenthesis() {
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    when(proposals.propose(any(), any(), title.capture(), any()))
        .thenReturn(new ProposedAction(UUID.randomUUID(), "create_task", "t", LocalDateTime.now()));

    tool().execute(arguments("Позвонить клиенту"), LAWYER);

    assertThat(title.getValue()).isEqualTo("Создать задачу «Позвонить клиенту»");
  }

  @Test
  void rejectsAMissingCaseIdBeforeCreatingAProposal() {
    AiToolResult result =
        tool().execute(MAPPER.createObjectNode().put("text", "Позвонить"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("caseId");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsATextLongerThanTheColumnBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(arguments("з".repeat(1001)), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("text");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsADueDateThatIsNotAnIsoDateBeforeCreatingAProposal() {
    AiToolResult result =
        tool().execute(arguments("Позвонить").put("dueDate", "01.09.2026"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("dueDate");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void performCreatesTheTaskUnderTheCaseFromTheArguments() {
    when(caseTaskService.create(eq(CASE_ID), any(), eq(LAWYER_ID), eq(List.of(ORG_ID))))
        .thenReturn(
            new CaseTaskResponse(
                UUID.randomUUID(),
                CASE_ID,
                "Подготовить отзыв",
                LocalDate.of(2026, 9, 1),
                false,
                LocalDateTime.now()));

    AiToolResult result =
        tool().perform(arguments("Подготовить отзыв").put("dueDate", "2026-09-01"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains(CASE_ID.toString()).contains("2026-09-01");
    ArgumentCaptor<CreateCaseTaskRequest> request =
        ArgumentCaptor.forClass(CreateCaseTaskRequest.class);
    verify(caseTaskService)
        .create(eq(CASE_ID), request.capture(), eq(LAWYER_ID), eq(List.of(ORG_ID)));
    assertThat(request.getValue().text()).isEqualTo("Подготовить отзыв");
  }

  @Test
  void schemaRequiresTheCaseAndTheTextAndForbidsUnknownFields() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required"))
        .extracting(JsonNode::asText)
        .containsExactlyInAnyOrder("caseId", "text");
  }

  private static ObjectNode arguments(String text) {
    return MAPPER.createObjectNode().put("caseId", CASE_ID.toString()).put("text", text);
  }
}
