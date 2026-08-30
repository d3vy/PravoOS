package com.pravoos.ai.recyclebin.api;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

public abstract class AbstractLeafSoftDeleteStore<T, R extends LeafSoftDeleteRepository<T>>
    implements SoftDeleteStore {

  protected final R repository;

  protected AbstractLeafSoftDeleteStore(R repository) {
    this.repository = repository;
  }

  protected abstract RuntimeException notFoundException(UUID id);

  protected abstract BinSnapshot snapshot(T entity);

  protected void onChange() {}

  @Override
  public BinContents moveToBin(String entityId, DeletionActor actor, boolean cascade) {
    UUID id = UUID.fromString(entityId);
    T entity = repository.findById(id).orElseThrow(() -> notFoundException(id));
    BinContents contents = BinContents.of(snapshot(entity));
    repository.softDelete(id, LocalDateTime.now(ZoneOffset.UTC));
    onChange();
    return contents;
  }

  @Override
  public void restore(String entityId) {
    repository.restore(UUID.fromString(entityId));
    onChange();
  }

  @Override
  public void purge(String entityId) {
    repository.hardDelete(UUID.fromString(entityId));
    onChange();
  }
}
