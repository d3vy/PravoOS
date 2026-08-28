package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AbstractAiWriteTool;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.InvalidToolArgumentException;
import com.pravoos.ai.core.api.ToolArguments;
import com.pravoos.ai.core.api.ToolJson;
import com.pravoos.ai.core.api.ToolSchema;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.shared.model.enums.DeadlineType;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AddCaseDeadlineTool extends AbstractAiWriteTool<AddCaseDeadlineTool.Command> {

  private static final String ALREADY_SET =
      "Срок такого типа у дела уже заполнен — он не перезаписан. "
          + "Сообщи пользователю и предложи изменить его вручную.";

  private final CaseService caseService;
  private final ObjectMapper objectMapper;

  public AddCaseDeadlineTool(
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
    return "add_deadline";
  }

  @Override
  public String description() {
    return "Готовит установку процессуального срока по делу: срока подачи, даты заседания "
        + "или даты истечения. Уже заполненный срок не перезаписывается. Действие "
        + "выполняется только после подтверждения пользователем.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredUuid("caseId", "Идентификатор дела (UUID)")
        .requiredEnum(
            "type",
            "Тип срока: FILING_DEADLINE — срок подачи, NEXT_HEARING — заседание, "
                + "EXPIRY — истечение срока",
            DeadlineType.FILING_DEADLINE,
            DeadlineType.NEXT_HEARING,
            DeadlineType.EXPIRY)
        .requiredDate("date", "Дата в формате ГГГГ-ММ-ДД")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  protected Command parse(JsonNode arguments) {
    DeadlineType type = ToolArguments.requireEnum(arguments, "type", DeadlineType.class);
    if (type == DeadlineType.TASK) {
      throw new InvalidToolArgumentException(
          "Тип TASK относится к задаче, а не к делу — используй create_task.");
    }
    return new Command(
        ToolArguments.requireUuid(arguments, "caseId"),
        type,
        ToolArguments.requireDate(arguments, "date"));
  }

  @Override
  protected String describe(Command command) {
    return "Установить срок «" + command.type().getDisplayName() + "» на " + command.date();
  }

  @Override
  protected AiToolResult run(Command command, AiToolContext context) {
    boolean applied =
        caseService.setDeadlineIfAbsent(
            command.caseId(), command.type(), command.date(), context.userId(), context.orgIds());
    if (!applied) {
      return AiToolResult.error(ALREADY_SET);
    }
    return AiToolResult.ok(
        ToolJson.write(
            objectMapper,
            new AppliedDeadlineView(
                command.caseId().toString(), command.type().name(), command.date().toString())));
  }

  public record Command(
      @NotNull UUID caseId, @NotNull DeadlineType type, @NotNull LocalDate date) {}

  private record AppliedDeadlineView(String caseId, String type, String date) {}
}
