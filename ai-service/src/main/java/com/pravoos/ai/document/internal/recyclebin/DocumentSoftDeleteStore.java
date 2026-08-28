package com.pravoos.ai.document.internal.recyclebin;

import com.pravoos.ai.document.internal.service.DocumentService;
import com.pravoos.ai.recyclebin.api.BinContents;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.api.SoftDeleteStore;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentSoftDeleteStore implements SoftDeleteStore {

  private final DocumentService documentService;

  public DocumentSoftDeleteStore(DocumentService documentService) {
    this.documentService = documentService;
  }

  @Override
  public RecycleBinEntityType entityType() {
    return RecycleBinEntityType.DOCUMENT;
  }

  @Override
  public BinContents moveToBin(String entityId, DeletionActor actor, boolean cascade) {
    return BinContents.of(documentService.moveToBin(UUID.fromString(entityId)));
  }

  @Override
  public void restore(String entityId) {
    documentService.restoreFromBin(UUID.fromString(entityId));
  }

  @Override
  public void purge(String entityId) {
    documentService.purge(UUID.fromString(entityId));
  }
}
