package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActorRole;
import com.pravoos.ai.core.api.AiToolContext;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.UUID;

final class AiWriteToolFixtures {

  static final ObjectMapper MAPPER = new ObjectMapper();
  static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
  static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

  static final UUID LAWYER_ID = UUID.randomUUID();
  static final UUID ORG_ID = UUID.randomUUID();
  static final AiToolContext LAWYER =
      new AiToolContext(LAWYER_ID, List.of(ORG_ID), AiActorRole.LAWYER, "conv-1");
  static final AiToolContext ADMIN =
      new AiToolContext(UUID.randomUUID(), List.of(), AiActorRole.ADMIN, "conv-1");

  private AiWriteToolFixtures() {}
}
