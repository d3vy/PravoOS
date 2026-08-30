package com.pravoos.ai.practice.internal.recyclebin;

import com.pravoos.ai.practice.internal.model.entity.DocumentTemplate;
import com.pravoos.ai.practice.internal.repository.jpa.DocumentTemplateRepository;
import com.pravoos.ai.practice.internal.service.TemplateCatalogCache;
import com.pravoos.ai.recyclebin.api.AbstractLeafSoftDeleteStore;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.TemplateNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemplateSoftDeleteStore
    extends AbstractLeafSoftDeleteStore<DocumentTemplate, DocumentTemplateRepository> {

  private final TemplateCatalogCache catalogCache;

  public TemplateSoftDeleteStore(
      DocumentTemplateRepository templateRepository, TemplateCatalogCache catalogCache) {
    super(templateRepository);
    this.catalogCache = catalogCache;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.TEMPLATE;
  }

  @Override
  protected RuntimeException notFoundException(UUID id) {
    return new TemplateNotFoundException(id);
  }

  @Override
  protected BinSnapshot snapshot(DocumentTemplate template) {
    return new BinSnapshot(
        RecycleBinEntityType.TEMPLATE,
        template.getId().toString(),
        template.getName(),
        template.getLawyerId(),
        null,
        Map.of());
  }

  @Override
  protected void onChange() {
    catalogCache.evictAll();
  }
}
