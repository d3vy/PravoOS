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
import com.pravoos.ai.practice.internal.dto.ClientDetailResponse;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.shared.exception.ClientNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetClientTool implements AiTool {

  private static final String NOT_AVAILABLE = "Клиент не найден или недоступен.";
  private static final int MAX_LISTED_CASES = 10;

  private final ClientService clientService;
  private final ObjectMapper objectMapper;

  public GetClientTool(ClientService clientService, ObjectMapper objectMapper) {
    this.clientService = clientService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "get_client";
  }

  @Override
  public String description() {
    return "Возвращает карточку клиента по его идентификатору: имя, тип, контакты, ИНН "
        + "и краткий список его дел. Идентификатор клиента можно взять из карточки дела "
        + "или из контекста страницы, которую открыл пользователь.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredUuid("clientId", "Идентификатор клиента (UUID)")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  public AiToolResult execute(JsonNode arguments, AiToolContext context) {
    UUID clientId;
    try {
      clientId = ToolArguments.requireUuid(arguments, "clientId");
    } catch (InvalidToolArgumentException ex) {
      return AiToolResult.error(ex.getMessage());
    }

    try {
      ClientDetailResponse response = clientService.get(clientId, context.userId());
      return AiToolResult.ok(ToolJson.write(objectMapper, ClientView.from(response)));
    } catch (ClientNotFoundException ex) {
      return AiToolResult.error(NOT_AVAILABLE);
    }
  }

  private record ClientView(
      String clientId,
      String name,
      String type,
      String phone,
      String email,
      String inn,
      long caseCount,
      List<CaseSummary> cases) {

    private static ClientView from(ClientDetailResponse response) {
      List<CaseSummary> cases =
          response.cases().stream().limit(MAX_LISTED_CASES).map(CaseSummary::from).toList();
      return new ClientView(
          response.client().id().toString(),
          response.client().name(),
          response.client().typeName(),
          response.client().phone(),
          response.client().email(),
          response.client().inn(),
          response.client().caseCount(),
          cases);
    }
  }

  private record CaseSummary(String caseId, String title, String status, String nextHearingDate) {

    private static CaseSummary from(CaseResponse response) {
      return new CaseSummary(
          response.id().toString(),
          response.title(),
          response.statusName(),
          ToolJson.text(response.nextHearingDate()));
    }
  }
}
