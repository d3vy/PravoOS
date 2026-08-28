package com.pravoos.ai.practice.internal.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.practice.internal.dto.ClientResponse;
import com.pravoos.ai.practice.internal.dto.CreateClientRequest;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.shared.exception.AiWriteActionRateLimitException;
import com.pravoos.ai.shared.model.enums.ClientType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateClientToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ValidatorFactory VALIDATOR_FACTORY =
      Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

  private static final UUID LAWYER_ID = UUID.randomUUID();
  private static final AiToolContext CONTEXT =
      new AiToolContext(LAWYER_ID, List.of(UUID.randomUUID()), AiActorRole.LAWYER, "conv-1");

  @Mock private AiActionProposals proposals;
  @Mock private ClientService clientService;

  @AfterAll
  static void closeValidator() {
    VALIDATOR_FACTORY.close();
  }

  private CreateClientTool tool() {
    return new CreateClientTool(proposals, VALIDATOR, clientService, MAPPER);
  }

  @Test
  void isAWriteToolThatIsNeitherReadOnlyNorAutoExecuted() {
    assertThat(tool().readOnly()).isFalse();
    assertThat(tool().requiresApproval()).isTrue();
  }

  @Test
  void isOfferedToLawyersOnlyAndHiddenFromAdmins() {
    assertThat(tool().availableFor(CONTEXT)).isTrue();
    assertThat(
            tool()
                .availableFor(
                    new AiToolContext(UUID.randomUUID(), List.of(), AiActorRole.ADMIN, "conv-1")))
        .isFalse();
  }

  @Test
  void executeOnlyProposesAndNeverCreatesTheClient() {
    when(proposals.propose(any(), any(), any(), any()))
        .thenReturn(
            new ProposedAction(
                UUID.randomUUID(),
                "create_client",
                "Создать клиента «Иванов»",
                LocalDateTime.now().plusMinutes(30)));

    AiToolResult result = tool().execute(argumentsFor("Иванов", "INDIVIDUAL"), CONTEXT);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("ждёт подтверждения").contains("Иванов");
    verify(clientService, never()).create(any(), any());
  }

  @Test
  void proposalTitleNamesTheClientAndItsType() {
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    when(proposals.propose(any(), any(), title.capture(), any()))
        .thenReturn(
            new ProposedAction(UUID.randomUUID(), "create_client", "t", LocalDateTime.now()));

    tool().execute(argumentsFor("ООО Ромашка", "COMPANY"), CONTEXT);

    assertThat(title.getValue()).contains("ООО Ромашка").contains("Юрлицо");
  }

  @Test
  void rejectsInvalidArgumentsBeforeCreatingAProposal() {
    AiToolResult result = tool().execute(argumentsFor("Иванов", "SOMETHING_ELSE"), CONTEXT);

    assertThat(result.ok()).isFalse();
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void rejectsArgumentsThatFailBeanValidationBeforeCreatingAProposal() {
    ObjectNode arguments = argumentsFor("Иванов", "INDIVIDUAL");
    arguments.put("inn", "123");

    AiToolResult result = tool().execute(arguments, CONTEXT);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("inn");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void reportsRateLimitToTheModelInsteadOfBlowingUpTheTurn() {
    when(proposals.propose(any(), any(), any(), any()))
        .thenThrow(new AiWriteActionRateLimitException());

    AiToolResult result = tool().execute(argumentsFor("Иванов", "INDIVIDUAL"), CONTEXT);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("лимит");
  }

  @Test
  void performCreatesTheClientWithTheActorFromTheContext() {
    when(clientService.create(any(), eq(LAWYER_ID)))
        .thenReturn(
            new ClientResponse(
                UUID.randomUUID(),
                "Иванов",
                ClientType.INDIVIDUAL,
                "Физлицо",
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                0L));

    AiToolResult result = tool().perform(argumentsFor("Иванов", "INDIVIDUAL"), CONTEXT);

    assertThat(result.ok()).isTrue();
    ArgumentCaptor<CreateClientRequest> request =
        ArgumentCaptor.forClass(CreateClientRequest.class);
    verify(clientService).create(request.capture(), eq(LAWYER_ID));
    assertThat(request.getValue().name()).isEqualTo("Иванов");
    assertThat(request.getValue().personalDataConsent()).isTrue();
  }

  @Test
  void schemaDeclaresTheClientTypeEnumAndForbidsUnknownFields() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required")).hasSize(2);
    assertThat(schema.get("properties").get("type").get("enum"))
        .extracting(JsonNode::asText)
        .containsExactly("INDIVIDUAL", "COMPANY");
  }

  private static ObjectNode argumentsFor(String name, String type) {
    return MAPPER.createObjectNode().put("name", name).put("type", type);
  }
}
