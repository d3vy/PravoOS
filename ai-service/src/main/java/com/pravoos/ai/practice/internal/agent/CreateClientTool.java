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
import com.pravoos.ai.practice.internal.dto.ClientResponse;
import com.pravoos.ai.practice.internal.dto.CreateClientRequest;
import com.pravoos.ai.practice.internal.service.ClientService;
import com.pravoos.ai.shared.model.enums.ClientType;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class CreateClientTool extends AbstractAiWriteTool<CreateClientRequest> {

  private final ClientService clientService;
  private final ObjectMapper objectMapper;

  public CreateClientTool(
      AiActionProposals proposals,
      Validator validator,
      ClientService clientService,
      ObjectMapper objectMapper) {
    super(proposals, validator);
    this.clientService = clientService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "create_client";
  }

  @Override
  public String description() {
    return "Готовит создание карточки клиента: имя или наименование, тип (физлицо или юрлицо), "
        + "телефон, e-mail, ИНН и заметку. Действие не выполняется сразу — пользователь "
        + "подтверждает его карточкой в чате, подтверждение и означает согласие на обработку "
        + "персональных данных.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredString("name", "Имя физлица или наименование организации")
        .requiredEnum(
            "type", "Тип клиента: INDIVIDUAL — физлицо, COMPANY — юрлицо", ClientType.class)
        .optionalString("phone", "Телефон в формате +7XXXXXXXXXX")
        .optionalString("email", "Адрес электронной почты")
        .optionalString("inn", "ИНН: 10 цифр для юрлица, 12 для физлица")
        .optionalString("notes", "Произвольная заметка о клиенте")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  protected CreateClientRequest parse(JsonNode arguments) {
    return new CreateClientRequest(
        ToolArguments.requireText(arguments, "name"),
        ToolArguments.requireEnum(arguments, "type", ClientType.class),
        ToolArguments.optionalText(arguments, "phone"),
        ToolArguments.optionalText(arguments, "email"),
        ToolArguments.optionalText(arguments, "inn"),
        ToolArguments.optionalText(arguments, "notes"),
        Boolean.TRUE);
  }

  @Override
  protected String describe(CreateClientRequest command) {
    return "Создать клиента «" + command.name() + "» (" + command.type().getDisplayName() + ")";
  }

  @Override
  protected AiToolResult run(CreateClientRequest command, AiToolContext context) {
    ClientResponse created = clientService.create(command, context.userId());
    return AiToolResult.ok(
        ToolJson.write(
            objectMapper,
            new CreatedClientView(created.id().toString(), created.name(), created.typeName())));
  }

  private record CreatedClientView(String clientId, String name, String type) {}
}
