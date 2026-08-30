package com.pravoos.ai.practice.internal.recyclebin;

import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.practice.internal.repository.jpa.WorkflowDefinitionRepository;
import com.pravoos.ai.practice.internal.service.WorkflowDefinitionCatalogCache;
import com.pravoos.ai.recyclebin.api.AbstractLeafSoftDeleteStore;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.WorkflowDefinitionNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkflowDefinitionSoftDeleteStore
    extends AbstractLeafSoftDeleteStore<WorkflowDefinition, WorkflowDefinitionRepository> {

  private final WorkflowDefinitionCatalogCache catalogCache;

  public WorkflowDefinitionSoftDeleteStore(
      WorkflowDefinitionRepository definitionRepository,
      WorkflowDefinitionCatalogCache catalogCache) {
    super(definitionRepository);
    this.catalogCache = catalogCache;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.WORKFLOW_DEFINITION;
  }

  @Override
  protected RuntimeException notFoundException(UUID id) {
    return new WorkflowDefinitionNotFoundException(id);
  }

  @Override
  protected BinSnapshot snapshot(WorkflowDefinition definition) {
    return new BinSnapshot(
        RecycleBinEntityType.WORKFLOW_DEFINITION,
        definition.getId().toString(),
        definition.getName(),
        definition.getCreatedBy(),
        definition.getOrgId(),
        Map.of());
  }

  @Override
  protected void onChange() {
    catalogCache.evictAll();
  }
}
