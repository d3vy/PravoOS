package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AbstractAiWriteTool;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.ToolArguments;
import com.pravoos.ai.core.api.ToolJson;
import com.pravoos.ai.core.api.ToolSchema;
import com.pravoos.ai.practice.internal.dto.CaseResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseRequest;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class CreateCaseTool extends AbstractAiWriteTool<CreateCaseRequest> {

  private final CaseService caseService;
  private final ObjectMapper objectMapper;

  public CreateCaseTool(
      AiActionProposals proposals,
      Validator validator,
      CaseService caseService,
      ObjectMapper objectMapper) {
    super(proposals, validator);
    this.caseService = caseService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "create_case";
  }

  @Override
  public String description() {
    return "Готовит создание судебного дела: название, описание, клиент, номер дела в суде, "
        + "судебная система и процессуальные сроки. Идентификатор клиента бери из "
        + "search_workspace или get_client — не выдумывай его. Действие выполняется только "
        + "после подтверждения пользователем.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredString("title", "Название дела")
        .optionalString("description", "Краткое описание сути дела")
        .optionalUuid("clientId", "Идентификатор клиента, к которому относится дело")
        .optionalDate("filingDeadline", "Срок подачи в формате ГГГГ-ММ-ДД")
        .optionalDate("nextHearingDate", "Дата ближайшего заседания в формате ГГГГ-ММ-ДД")
        .optionalString("courtCaseNumber", "Номер дела в суде")
        .optionalEnum("courtSystem", "Судебная система", CourtSystem.class)
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  protected CreateCaseRequest parse(JsonNode arguments) {
    return new CreateCaseRequest(
        ToolArguments.requireText(arguments, "title"),
        ToolArguments.optionalText(arguments, "description"),
        ToolArguments.optionalUuid(arguments, "clientId"),
        null,
        ToolArguments.optionalDate(arguments, "filingDeadline"),
        ToolArguments.optionalDate(arguments, "nextHearingDate"),
        null,
        ToolArguments.optionalText(arguments, "courtCaseNumber"),
        ToolArguments.optionalEnum(arguments, "courtSystem", CourtSystem.class),
        null);
  }

  @Override
  protected String describe(CreateCaseRequest command) {
    return "Создать дело «" + command.title() + "»";
  }

  @Override
  protected AiToolResult run(CreateCaseRequest command, AiToolContext context) {
    CaseResponse created = caseService.create(command, context.userId(), context.orgIds());
    return AiToolResult.ok(
        ToolJson.write(
            objectMapper,
            new CreatedCaseView(
                created.id().toString(),
                created.title(),
                created.statusName(),
                created.clientName())));
  }

  private record CreatedCaseView(String caseId, String title, String status, String clientName) {}
}
