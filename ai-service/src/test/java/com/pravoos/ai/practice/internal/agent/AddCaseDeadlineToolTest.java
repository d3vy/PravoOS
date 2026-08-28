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
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.shared.model.enums.DeadlineType;
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
class AddCaseDeadlineToolTest {

  private static final UUID CASE_ID = UUID.randomUUID();

  @Mock private AiActionProposals proposals;
  @Mock private CaseService caseService;

  private AddCaseDeadlineTool tool() {
    return new AddCaseDeadlineTool(proposals, VALIDATOR, caseService, MAPPER);
  }

  @Test
  void isAWriteToolOfferedToLawyersOnly() {
    assertThat(tool().readOnly()).isFalse();
    assertThat(tool().requiresApproval()).isTrue();
    assertThat(tool().availableFor(LAWYER)).isTrue();
    assertThat(tool().availableFor(ADMIN)).isFalse();
  }

  @Test
  void executeOnlyProposesAndNeverTouchesTheCase() {
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    when(proposals.propose(any(), any(), title.capture(), any()))
        .thenReturn(
            new ProposedAction(
                UUID.randomUUID(), "add_deadline", "t", LocalDateTime.now().plusMinutes(30)));

    AiToolResult result = tool().execute(arguments("NEXT_HEARING", "2026-09-01"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(title.getValue()).contains("Судебное заседание").contains("2026-09-01");
    verify(caseService, never()).setDeadlineIfAbsent(any(), any(), any(), any(), any());
  }

  @Test
  void refusesTheTaskDeadlineTypeThatBelongsToTasksNotCases() {
    AiToolResult result = tool().execute(arguments("TASK", "2026-09-01"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("create_task");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void schemaDoesNotAdvertiseTheTaskDeadlineTypeToTheModel() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("properties").get("type").get("enum"))
        .extracting(JsonNode::asText)
        .containsExactly("FILING_DEADLINE", "NEXT_HEARING", "EXPIRY");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required"))
        .extracting(JsonNode::asText)
        .containsExactlyInAnyOrder("caseId", "type", "date");
  }

  @Test
  void rejectsAnUnknownDeadlineTypeBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(arguments("WHENEVER", "2026-09-01"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("type");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsADateThatIsNotAnIsoDateBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(arguments("EXPIRY", "01.09.2026"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("date");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void performSetsTheDeadlineWithTheActorAndOrganizationsFromTheContext() {
    when(caseService.setDeadlineIfAbsent(
            eq(CASE_ID),
            eq(DeadlineType.FILING_DEADLINE),
            eq(LocalDate.of(2026, 9, 1)),
            eq(LAWYER_ID),
            eq(List.of(ORG_ID))))
        .thenReturn(true);

    AiToolResult result = tool().perform(arguments("FILING_DEADLINE", "2026-09-01"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("FILING_DEADLINE").contains("2026-09-01");
  }

  @Test
  void reportsAnAlreadyFilledDeadlineAsAnErrorInsteadOfOverwritingIt() {
    when(caseService.setDeadlineIfAbsent(any(), any(), any(), any(), any())).thenReturn(false);

    AiToolResult result = tool().perform(arguments("EXPIRY", "2026-09-01"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("не перезаписан");
  }

  private static ObjectNode arguments(String type, String date) {
    return MAPPER
        .createObjectNode()
        .put("caseId", CASE_ID.toString())
        .put("type", type)
        .put("date", date);
  }
}
