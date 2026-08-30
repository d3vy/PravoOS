package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AbstractAiWriteTool;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.DocumentAccessGuard;
import com.pravoos.ai.core.api.ToolArguments;
import com.pravoos.ai.core.api.ToolJson;
import com.pravoos.ai.core.api.ToolSchema;
import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.practice.internal.service.CaseService;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LinkDocumentToCaseTool extends AbstractAiWriteTool<LinkDocumentToCaseTool.Command> {

  private final DocumentCommand documentCommand;
  private final DocumentAccessGuard documentAccessGuard;
  private final CaseService caseService;
  private final ObjectMapper objectMapper;

  public LinkDocumentToCaseTool(
      AiActionProposals proposals,
      Validator validator,
      DocumentCommand documentCommand,
      DocumentAccessGuard documentAccessGuard,
      CaseService caseService,
      ObjectMapper objectMapper) {
    super(proposals, validator);
    this.documentCommand = documentCommand;
    this.documentAccessGuard = documentAccessGuard;
    this.caseService = caseService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "link_document_to_case";
  }

  @Override
  public String description() {
    return "Готовит привязку уже загруженного документа к делу. Документ, который уже "
        + "принадлежит другому делу, не переносится. Действие выполняется только после "
        + "подтверждения пользователем.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredUuid("documentId", "Идентификатор документа (UUID)")
        .requiredUuid("caseId", "Идентификатор дела (UUID)")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  protected Command parse(JsonNode arguments) {
    return new Command(
        ToolArguments.requireUuid(arguments, "documentId"),
        ToolArguments.requireUuid(arguments, "caseId"));
  }

  @Override
  protected String describe(Command command) {
    return "Привязать документ " + command.documentId() + " к делу " + command.caseId();
  }

  @Override
  protected AiToolResult run(Command command, AiToolContext context) {
    documentAccessGuard.requireVisible(command.documentId(), context.userId(), context.orgIds());
    caseService.requireVisibleCase(command.caseId(), context.userId(), context.orgIds());
    DocumentResponse linked = documentCommand.attachToCase(command.documentId(), command.caseId());
    return AiToolResult.ok(
        ToolJson.write(
            objectMapper,
            new LinkedDocumentView(
                linked.id().toString(), linked.title(), command.caseId().toString())));
  }

  public record Command(@NotNull UUID documentId, @NotNull UUID caseId) {}

  private record LinkedDocumentView(String documentId, String title, String caseId) {}
}
