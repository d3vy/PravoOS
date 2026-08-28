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
import com.pravoos.ai.practice.internal.dto.CreateInvoiceRequest;
import com.pravoos.ai.practice.internal.dto.InvoiceResponse;
import com.pravoos.ai.practice.internal.service.InvoiceService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class CreateInvoiceTool extends AbstractAiWriteTool<CreateInvoiceRequest> {

  private final InvoiceService invoiceService;
  private final ObjectMapper objectMapper;

  public CreateInvoiceTool(
      AiActionProposals proposals,
      Validator validator,
      InvoiceService invoiceService,
      ObjectMapper objectMapper) {
    super(proposals, validator);
    this.invoiceService = invoiceService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "create_invoice";
  }

  @Override
  public String description() {
    return "Готовит выставление счёта клиенту по неоплаченным записям учёта времени. "
        + "Счёт собирается из списанного времени: если его нет, счёт выставить нельзя. "
        + "Действие выполняется только после подтверждения пользователем.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredUuid("clientId", "Идентификатор клиента (UUID)")
        .optionalUuid("caseId", "Идентификатор дела, если счёт только по одному делу")
        .optionalDate("dueDate", "Срок оплаты в формате ГГГГ-ММ-ДД")
        .optionalNumber("vatRate", "Ставка НДС в процентах, например 20")
        .optionalString("notes", "Примечание к счёту")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  protected CreateInvoiceRequest parse(JsonNode arguments) {
    return new CreateInvoiceRequest(
        ToolArguments.requireUuid(arguments, "clientId"),
        ToolArguments.optionalUuid(arguments, "caseId"),
        null,
        ToolArguments.optionalDate(arguments, "dueDate"),
        ToolArguments.optionalDecimal(arguments, "vatRate"),
        ToolArguments.optionalText(arguments, "notes"));
  }

  @Override
  protected String describe(CreateInvoiceRequest command) {
    return command.caseId() == null
        ? "Выставить счёт клиенту по всему несписанному времени"
        : "Выставить счёт клиенту по делу " + command.caseId();
  }

  @Override
  protected AiToolResult run(CreateInvoiceRequest command, AiToolContext context) {
    InvoiceResponse created = invoiceService.create(command, context.userId());
    return AiToolResult.ok(
        ToolJson.write(
            objectMapper,
            new CreatedInvoiceView(
                created.id().toString(),
                created.number(),
                created.clientName(),
                ToolJson.text(created.total()),
                created.statusLabel())));
  }

  private record CreatedInvoiceView(
      String invoiceId, String number, String clientName, String total, String status) {}
}
