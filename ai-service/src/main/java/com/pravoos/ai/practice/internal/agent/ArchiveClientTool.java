package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class ArchiveClientTool extends ArchiveEntityTool {

  public ArchiveClientTool(
      AiActionProposals proposals,
      Validator validator,
      RecycleBin recycleBin,
      ObjectMapper objectMapper) {
    super(proposals, validator, recycleBin, objectMapper);
  }

  @Override
  public String name() {
    return "archive_client";
  }

  @Override
  public String description() {
    return "Готовит перемещение карточки клиента в корзину вместе с его делами. Клиента "
        + "можно восстановить из корзины в течение срока хранения. Безвозвратное удаление "
        + "недоступно. Действие выполняется только после подтверждения пользователем.";
  }

  @Override
  protected RecycleBinEntityType entityType() {
    return RecycleBinEntityType.CLIENT;
  }

  @Override
  protected String argumentName() {
    return "clientId";
  }

  @Override
  protected String entityLabel() {
    return "клиента";
  }
}
