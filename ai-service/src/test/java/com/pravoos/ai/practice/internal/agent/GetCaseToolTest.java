package com.pravoos.ai.practice.internal.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.practice.internal.dto.CaseResponse;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCaseToolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final UUID LAWYER_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();
  private static final UUID CASE_ID = UUID.randomUUID();
  private static final UUID CLIENT_ID = UUID.randomUUID();
  private static final AiToolContext CONTEXT =
      new AiToolContext(LAWYER_ID, List.of(ORG_ID), AiActorRole.LAWYER);

  @Mock private CaseService caseService;

  private GetCaseTool tool() {
    return new GetCaseTool(caseService, MAPPER);
  }

  @Test
  void isOfferedToLawyersOnlyAndHiddenFromAdmins() {
    UUID adminId = UUID.randomUUID();

    assertThat(tool().availableFor(CONTEXT)).isTrue();
    assertThat(tool().availableFor(new AiToolContext(adminId, List.of(), AiActorRole.ADMIN)))
        .isFalse();
  }

  @Test
  void exposesUuidSchemaWithRequiredCaseId() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("type").asText()).isEqualTo("object");
    assertThat(schema.get("required").get(0).asText()).isEqualTo("caseId");
    assertThat(schema.get("properties").get("caseId").get("type").asText()).isEqualTo("string");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
  }

  @Test
  void readsCaseThroughServiceWithActorFromContext() throws Exception {
    when(caseService.get(CASE_ID, LAWYER_ID, List.of(ORG_ID))).thenReturn(caseResponse());

    AiToolResult result = tool().execute(argumentsWith("caseId", CASE_ID.toString()), CONTEXT);

    assertThat(result.ok()).isTrue();
    JsonNode payload = MAPPER.readTree(result.content());
    assertThat(payload.get("caseId").asText()).isEqualTo(CASE_ID.toString());
    assertThat(payload.get("title").asText()).isEqualTo("Иванов против ООО Ромашка");
    assertThat(payload.get("clientId").asText()).isEqualTo(CLIENT_ID.toString());
    assertThat(payload.get("nextHearingDate").asText()).isEqualTo("2026-09-15");
    verify(caseService).get(CASE_ID, LAWYER_ID, List.of(ORG_ID));
  }

  @Test
  void doesNotLeakInternalOwnershipFields() throws Exception {
    when(caseService.get(any(), any(), any())).thenReturn(caseResponse());

    AiToolResult result = tool().execute(argumentsWith("caseId", CASE_ID.toString()), CONTEXT);

    JsonNode payload = MAPPER.readTree(result.content());
    assertThat(payload.has("ownerId")).isFalse();
    assertThat(payload.has("orgId")).isFalse();
  }

  @Test
  void ignoresOrgIdSuppliedByTheModel() {
    when(caseService.get(CASE_ID, LAWYER_ID, List.of(ORG_ID))).thenReturn(caseResponse());

    var arguments = MAPPER.createObjectNode();
    arguments.put("caseId", CASE_ID.toString());
    arguments.put("orgId", UUID.randomUUID().toString());
    arguments.put("lawyerId", UUID.randomUUID().toString());

    tool().execute(arguments, CONTEXT);

    verify(caseService).get(CASE_ID, LAWYER_ID, List.of(ORG_ID));
  }

  @Test
  void reportsNotAvailableWithoutDistinguishingMissingFromForbidden() {
    when(caseService.get(any(), any(), any())).thenThrow(new CaseNotFoundException(CASE_ID));

    AiToolResult result = tool().execute(argumentsWith("caseId", CASE_ID.toString()), CONTEXT);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).isEqualTo("Дело не найдено или недоступно.");
    assertThat(result.content()).doesNotContain(CASE_ID.toString());
  }

  @Test
  void rejectsMalformedUuidWithoutCallingService() {
    AiToolResult result = tool().execute(argumentsWith("caseId", "не-uuid"), CONTEXT);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("UUID");
    verify(caseService, never()).get(any(), any(), any());
  }

  @Test
  void rejectsMissingArgumentWithoutCallingService() {
    AiToolResult result = tool().execute(MAPPER.createObjectNode(), CONTEXT);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("caseId");
    verify(caseService, never()).get(any(), any(), eq(List.of(ORG_ID)));
  }

  private static JsonNode argumentsWith(String field, String value) {
    return MAPPER.createObjectNode().put(field, value);
  }

  private static CaseResponse caseResponse() {
    return new CaseResponse(
        CASE_ID,
        LAWYER_ID,
        ORG_ID,
        "Иванов против ООО Ромашка",
        "Спор о взыскании задолженности",
        CLIENT_ID,
        "Иванов И.И.",
        CaseStatus.IN_PROGRESS,
        CaseStatus.IN_PROGRESS.getDisplayName(),
        LocalDate.of(2026, 9, 1),
        LocalDate.of(2026, 9, 15),
        null,
        CourtSystem.ARBITR,
        CourtSystem.ARBITR.getDisplayName(),
        "А40-123/2026",
        null,
        null,
        LocalDateTime.of(2026, 1, 10, 12, 0));
  }
}
