package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

@Component
public class ArchiveCaseTool extends ArchiveEntityTool {

  public ArchiveCaseTool(
      AiActionProposals proposals,
      Validator validator,
      RecycleBin recycleBin,
      ObjectMapper objectMapper) {
    super(proposals, validator, recycleBin, objectMapper);
  }

  @Override
  public String name() {
    return "archive_case";
  }

  @Override
  public String description() {
    return "Готовит перемещение дела в корзину вместе с его документами. Дело можно "
        + "восстановить из корзины в течение срока хранения. Безвозвратное удаление "
        + "недоступно. Действие выполняется только после подтверждения пользователем.";
  }

  @Override
  protected RecycleBinEntityType entityType() {
    return RecycleBinEntityType.CASE;
  }

  @Override
  protected String argumentName() {
    return "caseId";
  }

  @Override
  protected String entityLabel() {
    return "дело";
  }
}
