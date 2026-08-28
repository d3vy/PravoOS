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
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

abstract class ArchiveEntityTool extends AbstractAiWriteTool<ArchiveEntityTool.Command> {

  private final RecycleBin recycleBin;
  private final ObjectMapper objectMapper;

  protected ArchiveEntityTool(
      AiActionProposals proposals,
      Validator validator,
      RecycleBin recycleBin,
      ObjectMapper objectMapper) {
    super(proposals, validator);
    this.recycleBin = recycleBin;
    this.objectMapper = objectMapper;
  }

  protected abstract RecycleBinEntityType entityType();

  protected abstract String argumentName();

  protected abstract String entityLabel();

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredUuid(argumentName(), "Идентификатор (UUID)")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  protected Command parse(JsonNode arguments) {
    return new Command(ToolArguments.requireUuid(arguments, argumentName()));
  }

  @Override
  protected String describe(Command command) {
    return "Переместить в корзину: " + entityLabel() + " " + command.entityId();
  }

  @Override
  protected AiToolResult run(Command command, AiToolContext context) {
    recycleBin.moveToBin(
        entityType(),
        command.entityId().toString(),
        new DeletionActor(
            context.userId(), DeletionRole.LAWYER, context.singleOrgId(), context.orgIds()),
        true);
    return AiToolResult.ok(
        ToolJson.write(
            objectMapper,
            new ArchivedView(command.entityId().toString(), entityType().name(), "RECYCLE_BIN")));
  }

  public record Command(@NotNull UUID entityId) {}

  private record ArchivedView(String entityId, String entityType, String movedTo) {}
}
