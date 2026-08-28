package com.pravoos.ai.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.shared.exception.AiWriteActionRateLimitException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractAiWriteTool<C> implements AiWriteTool {

  private static final String AWAITING_CONFIRMATION =
      "Действие подготовлено и ждёт подтверждения пользователя: ";
  private static final String RATE_LIMITED =
      "Превышен лимит действий, изменяющих данные. Сообщи пользователю, что нужно подождать.";

  private final AiActionProposals proposals;
  private final Validator validator;

  protected AbstractAiWriteTool(AiActionProposals proposals, Validator validator) {
    this.proposals = proposals;
    this.validator = validator;
  }

  @Override
  public final AiToolResult execute(JsonNode arguments, AiToolContext context) {
    C command;
    try {
      command = validated(parse(arguments));
    } catch (InvalidToolArgumentException ex) {
      return AiToolResult.error(ex.getMessage());
    }
    if (!requiresApproval() || proposals.isTrusted(name(), context)) {
      return run(command, context);
    }
    try {
      ProposedAction proposed = proposals.propose(name(), arguments, describe(command), context);
      return AiToolResult.ok(AWAITING_CONFIRMATION + proposed.title());
    } catch (AiWriteActionRateLimitException ex) {
      return AiToolResult.error(RATE_LIMITED);
    }
  }

  @Override
  public final AiToolResult perform(JsonNode arguments, AiToolContext context) {
    return run(validated(parse(arguments)), context);
  }

  @Override
  public final String title(JsonNode arguments) {
    return describe(validated(parse(arguments)));
  }

  protected abstract C parse(JsonNode arguments);

  protected abstract String describe(C command);

  protected abstract AiToolResult run(C command, AiToolContext context);

  private C validated(C command) {
    Set<ConstraintViolation<C>> violations = validator.validate(command);
    if (violations.isEmpty()) {
      return command;
    }
    throw new InvalidToolArgumentException(
        violations.stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .sorted()
            .collect(Collectors.joining("; ")));
  }
}
