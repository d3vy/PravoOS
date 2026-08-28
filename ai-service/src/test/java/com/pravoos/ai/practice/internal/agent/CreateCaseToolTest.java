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
import com.pravoos.ai.practice.internal.dto.CaseResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseRequest;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.shared.exception.AiWriteActionRateLimitException;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
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
class CreateCaseToolTest {

  @Mock private AiActionProposals proposals;
  @Mock private CaseService caseService;

  private CreateCaseTool tool() {
    return new CreateCaseTool(proposals, VALIDATOR, caseService, MAPPER);
  }

  @Test
  void isAWriteToolOfferedToLawyersOnly() {
    assertThat(tool().readOnly()).isFalse();
    assertThat(tool().requiresApproval()).isTrue();
    assertThat(tool().availableFor(LAWYER)).isTrue();
    assertThat(tool().availableFor(ADMIN)).isFalse();
  }

  @Test
  void executeOnlyProposesAndNeverCreatesTheCase() {
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    when(proposals.propose(any(), any(), title.capture(), any()))
        .thenReturn(
            new ProposedAction(
                UUID.randomUUID(), "create_case", "t", LocalDateTime.now().plusMinutes(30)));

    AiToolResult result = tool().execute(arguments("Иванов против ФНС"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(title.getValue()).contains("Иванов против ФНС");
    verify(caseService, never()).create(any(), any(), any());
  }

  @Test
  void rejectsAMissingTitleBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(MAPPER.createObjectNode(), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("title");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsAClientIdThatIsNotAUuidBeforeCreatingAProposal() {
    ObjectNode arguments = arguments("Дело").put("clientId", "не-uuid");

    AiToolResult result = tool().execute(arguments, LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("clientId");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsATitleLongerThanTheColumnBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(arguments("д".repeat(501)), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("title");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void reportsRateLimitToTheModelInsteadOfBlowingUpTheTurn() {
    when(proposals.propose(any(), any(), any(), any()))
        .thenThrow(new AiWriteActionRateLimitException());

    AiToolResult result = tool().execute(arguments("Дело"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("лимит");
  }

  @Test
  void performCreatesTheCaseWithTheActorAndOrganizationsFromTheContext() {
    when(caseService.create(any(), eq(LAWYER_ID), eq(List.of(ORG_ID)))).thenReturn(created());

    ObjectNode arguments = arguments("Иванов против ФНС");
    arguments.put("courtSystem", "ARBITR");
    arguments.put("filingDeadline", "2026-09-01");
    AiToolResult result = tool().perform(arguments, LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("caseId").contains("Иванов против ФНС");
    ArgumentCaptor<CreateCaseRequest> request = ArgumentCaptor.forClass(CreateCaseRequest.class);
    verify(caseService).create(request.capture(), eq(LAWYER_ID), eq(List.of(ORG_ID)));
    assertThat(request.getValue().courtSystem()).isEqualTo(CourtSystem.ARBITR);
    assertThat(request.getValue().filingDeadline()).isEqualTo(LocalDate.of(2026, 9, 1));
  }

  @Test
  void schemaRequiresOnlyTheTitleAndForbidsUnknownFields() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required")).extracting(JsonNode::asText).containsExactly("title");
    assertThat(schema.get("properties").get("clientId").get("format").asText()).isEqualTo("uuid");
  }

  private static ObjectNode arguments(String title) {
    return MAPPER.createObjectNode().put("title", title);
  }

  private static CaseResponse created() {
    return new CaseResponse(
        UUID.randomUUID(),
        LAWYER_ID,
        ORG_ID,
        "Иванов против ФНС",
        null,
        null,
        "Иванов",
        CaseStatus.IN_PROGRESS,
        "В работе",
        null,
        null,
        null,
        CourtSystem.ARBITR,
        "КАД.Арбитр",
        null,
        null,
        null,
        LocalDateTime.now());
  }
}
