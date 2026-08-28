package com.pravoos.ai.practice.internal.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.practice.internal.dto.CaseResponse;
import com.pravoos.ai.practice.internal.dto.ClientDetailResponse;
import com.pravoos.ai.practice.internal.dto.ClientResponse;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.shared.exception.ClientNotFoundException;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.ClientType;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetClientToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final UUID LAWYER_ID = UUID.randomUUID();
  private static final UUID CLIENT_ID = UUID.randomUUID();
  private static final AiToolContext CONTEXT =
      new AiToolContext(LAWYER_ID, List.of(UUID.randomUUID()), AiActorRole.LAWYER);

  @Mock private ClientService clientService;

  private GetClientTool tool() {
    return new GetClientTool(clientService, MAPPER);
  }

  @Test
  void isOfferedToLawyersOnlyAndHiddenFromAdmins() {
    UUID adminId = UUID.randomUUID();

    assertThat(tool().availableFor(CONTEXT)).isTrue();
    assertThat(tool().availableFor(new AiToolContext(adminId, List.of(), AiActorRole.ADMIN)))
        .isFalse();
  }

  @Test
  void exposesUuidSchemaWithRequiredClientId() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("required").get(0).asText()).isEqualTo("clientId");
    assertThat(schema.get("properties").get("clientId").get("type").asText()).isEqualTo("string");
  }

  @Test
  void readsClientOwnedByTheActor() throws Exception {
    when(clientService.get(CLIENT_ID, LAWYER_ID)).thenReturn(clientDetail(1));

    AiToolResult result = tool().execute(argumentsWith(CLIENT_ID.toString()), CONTEXT);

    assertThat(result.ok()).isTrue();
    JsonNode payload = MAPPER.readTree(result.content());
    assertThat(payload.get("clientId").asText()).isEqualTo(CLIENT_ID.toString());
    assertThat(payload.get("name").asText()).isEqualTo("Иванов И.И.");
    assertThat(payload.get("type").asText()).isEqualTo(ClientType.INDIVIDUAL.getDisplayName());
    assertThat(payload.get("cases")).hasSize(1);
    verify(clientService).get(CLIENT_ID, LAWYER_ID);
  }

  @Test
  void ignoresLawyerIdSuppliedByTheModel() {
    when(clientService.get(CLIENT_ID, LAWYER_ID)).thenReturn(clientDetail(0));

    var arguments = MAPPER.createObjectNode();
    arguments.put("clientId", CLIENT_ID.toString());
    arguments.put("lawyerId", UUID.randomUUID().toString());

    tool().execute(arguments, CONTEXT);

    verify(clientService).get(CLIENT_ID, LAWYER_ID);
  }

  @Test
  void capsTheListOfCases() throws Exception {
    when(clientService.get(CLIENT_ID, LAWYER_ID)).thenReturn(clientDetail(25));

    AiToolResult result = tool().execute(argumentsWith(CLIENT_ID.toString()), CONTEXT);

    JsonNode payload = MAPPER.readTree(result.content());
    assertThat(payload.get("cases")).hasSize(10);
    assertThat(payload.get("caseCount").asLong()).isEqualTo(25);
  }

  @Test
  void reportsNotAvailableForForeignClient() {
    when(clientService.get(any(), any())).thenThrow(new ClientNotFoundException(CLIENT_ID));

    AiToolResult result = tool().execute(argumentsWith(CLIENT_ID.toString()), CONTEXT);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).isEqualTo("Клиент не найден или недоступен.");
    assertThat(result.content()).doesNotContain(CLIENT_ID.toString());
  }

  @Test
  void rejectsMalformedUuidWithoutCallingService() {
    AiToolResult result = tool().execute(argumentsWith("не-uuid"), CONTEXT);

    assertThat(result.ok()).isFalse();
    verify(clientService, never()).get(any(), any());
  }

  private static JsonNode argumentsWith(String clientId) {
    return MAPPER.createObjectNode().put("clientId", clientId);
  }

  private static ClientDetailResponse clientDetail(int caseCount) {
    List<CaseResponse> cases =
        IntStream.range(0, caseCount).mapToObj(index -> caseResponse(index)).toList();
    ClientResponse client =
        new ClientResponse(
            CLIENT_ID,
            "Иванов И.И.",
            ClientType.INDIVIDUAL,
            ClientType.INDIVIDUAL.getDisplayName(),
            "+7 900 000-00-00",
            "ivanov@example.com",
            "770101000000",
            "заметка",
            LocalDateTime.of(2026, 1, 10, 12, 0),
            caseCount);
    return new ClientDetailResponse(client, cases);
  }

  private static CaseResponse caseResponse(int index) {
    return new CaseResponse(
        UUID.randomUUID(),
        LAWYER_ID,
        null,
        "Дело " + index,
        null,
        CLIENT_ID,
        "Иванов И.И.",
        CaseStatus.IN_PROGRESS,
        CaseStatus.IN_PROGRESS.getDisplayName(),
        null,
        null,
        null,
        CourtSystem.ARBITR,
        CourtSystem.ARBITR.getDisplayName(),
        null,
        null,
        null,
        LocalDateTime.of(2026, 1, 10, 12, 0));
  }
}
