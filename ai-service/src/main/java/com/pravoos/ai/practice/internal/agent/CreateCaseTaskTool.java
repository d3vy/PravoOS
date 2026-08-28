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
import com.pravoos.ai.practice.internal.dto.CaseTaskResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseTaskRequest;
import com.pravoos.ai.practice.internal.service.CaseTaskService;
import jakarta.validation.Validator;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreateCaseTaskTool extends AbstractAiWriteTool<CreateCaseTaskTool.Command> {

  private final CaseTaskService caseTaskService;
  private final ObjectMapper objectMapper;

  public CreateCaseTaskTool(
      AiActionProposals proposals,
      Validator validator,
      CaseTaskService caseTaskService,
      ObjectMapper objectMapper) {
    super(proposals, validator);
    this.caseTaskService = caseTaskService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "create_task";
  }

  @Override
  public String description() {
    return "Готовит создание задачи по делу: текст задачи и срок её выполнения. "
        + "Идентификатор дела бери из search_workspace, get_case или контекста страницы. "
        + "Действие выполняется только после подтверждения пользователем.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredUuid("caseId", "Идентификатор дела (UUID)")
        .requiredString("text", "Что нужно сделать")
        .optionalDate("dueDate", "Срок выполнения в формате ГГГГ-ММ-ДД")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  protected Command parse(JsonNode arguments) {
    return new Command(
        ToolArguments.requireUuid(arguments, "caseId"),
        new CreateCaseTaskRequest(
            ToolArguments.requireText(arguments, "text"),
            ToolArguments.optionalDate(arguments, "dueDate")));
  }

  @Override
  protected String describe(Command command) {
    String dueDate =
        command.request().dueDate() == null ? "" : " (до " + command.request().dueDate() + ")";
    return "Создать задачу «" + command.request().text() + "»" + dueDate;
  }

  @Override
  protected AiToolResult run(Command command, AiToolContext context) {
    CaseTaskResponse created =
        caseTaskService.create(
            command.caseId(), command.request(), context.userId(), context.orgIds());
    return AiToolResult.ok(
        ToolJson.write(
            objectMapper,
            new CreatedTaskView(
                created.id().toString(),
                command.caseId().toString(),
                created.text(),
                ToolJson.text(created.dueDate()))));
  }

  public record Command(UUID caseId, @jakarta.validation.Valid CreateCaseTaskRequest request) {}

  private record CreatedTaskView(String taskId, String caseId, String text, String dueDate) {}
}
