package com.pravoos.ai.recyclebin.api;

public interface SoftDeleteStore {

  RecycleBinEntityType entityType();

  BinContents moveToBin(String entityId, DeletionActor actor, boolean cascade);

  void restore(String entityId);

  void purge(String entityId);
}
