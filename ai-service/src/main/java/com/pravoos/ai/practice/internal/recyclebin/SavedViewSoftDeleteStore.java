package com.pravoos.ai.practice.internal.recyclebin;

import com.pravoos.ai.practice.internal.model.entity.SavedView;
import com.pravoos.ai.practice.internal.repository.jpa.SavedViewRepository;
import com.pravoos.ai.practice.internal.service.SavedViewCatalogCache;
import com.pravoos.ai.recyclebin.api.AbstractLeafSoftDeleteStore;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.SavedViewNotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SavedViewSoftDeleteStore
    extends AbstractLeafSoftDeleteStore<SavedView, SavedViewRepository> {

  private final SavedViewCatalogCache catalogCache;

  public SavedViewSoftDeleteStore(
      SavedViewRepository savedViewRepository, SavedViewCatalogCache catalogCache) {
    super(savedViewRepository);
    this.catalogCache = catalogCache;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.SAVED_VIEW;
  }

  @Override
  protected RuntimeException notFoundException(UUID id) {
    return new SavedViewNotFoundException(id);
  }

  @Override
  protected BinSnapshot snapshot(SavedView view) {
    return new BinSnapshot(
        RecycleBinEntityType.SAVED_VIEW,
        view.getId().toString(),
        view.getName(),
        view.getLawyerId(),
        view.getOrgId(),
        Map.of());
  }

  @Override
  protected void onChange() {
    catalogCache.evictAll();
  }
}
