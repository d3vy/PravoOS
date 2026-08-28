package com.pravoos.ai.recyclebin.api;

import java.util.UUID;

public interface RecycleBin {

  void moveToBin(RecycleBinEntityType entityType, String entityId, DeletionActor actor);

  void moveToBin(
      RecycleBinEntityType entityType, String entityId, DeletionActor actor, boolean cascade);

  void forgetOwner(UUID ownerId);
}
