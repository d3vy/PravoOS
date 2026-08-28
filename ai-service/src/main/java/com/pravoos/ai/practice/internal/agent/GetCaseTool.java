package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.InvalidToolArgumentException;
import com.pravoos.ai.core.api.ToolArguments;
import com.pravoos.ai.core.api.ToolJson;
import com.pravoos.ai.core.api.ToolSchema;
import com.pravoos.ai.practice.internal.dto.CaseResponse;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetCaseTool implements AiTool {

  private static final String NOT_AVAILABLE = "Дело не найдено или недоступно.";
  private static final int DESCRIPTION_MAX_CHARS = 1500;

  private final CaseService caseService;
  private final ObjectMapper objectMapper;

  public GetCaseTool(CaseService caseService, ObjectMapper objectMapper) {
    this.caseService = caseService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "get_case";
  }

  @Override
  public String description() {
    return "Возвращает карточку судебного дела по его идентификатору: название, статус, клиента, "
        + "номер дела в суде, ближайшее заседание и процессуальные сроки. "
        + "Идентификатор дела можно взять из контекста страницы, которую открыл пользователь.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredUuid("caseId", "Идентификатор дела (UUID)")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  public AiToolResult execute(JsonNode arguments, AiToolContext context) {
    UUID caseId;
    try {
      caseId = ToolArguments.requireUuid(arguments, "caseId");
    } catch (InvalidToolArgumentException ex) {
      return AiToolResult.error(ex.getMessage());
    }

    try {
      CaseResponse response = caseService.get(caseId, context.userId(), context.orgIds());
      return AiToolResult.ok(ToolJson.write(objectMapper, CaseView.from(response)));
    } catch (CaseNotFoundException ex) {
      return AiToolResult.error(NOT_AVAILABLE);
    }
  }

  private record CaseView(
      String caseId,
      String title,
      String description,
      String status,
      String clientId,
      String clientName,
      String courtSystem,
      String courtCaseNumber,
      String filingDeadline,
      String nextHearingDate,
      String createdAt) {

    private static CaseView from(CaseResponse response) {
      return new CaseView(
          response.id().toString(),
          response.title(),
          ToolJson.truncate(response.description(), DESCRIPTION_MAX_CHARS),
          response.statusName(),
          response.clientId() == null ? null : response.clientId().toString(),
          response.clientName(),
          response.courtSystemName(),
          response.courtCaseNumber(),
          ToolJson.text(response.filingDeadline()),
          ToolJson.text(response.nextHearingDate()),
          ToolJson.text(response.createdAt()));
    }
  }
}
