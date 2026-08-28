package com.pravoos.ai.recyclebin.internal.service;

import com.pravoos.ai.recyclebin.api.BinContents;
import com.pravoos.ai.recyclebin.api.BinSnapshot;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.api.SoftDeleteStore;
import com.pravoos.ai.recyclebin.internal.config.RecycleBinProperties;
import com.pravoos.ai.recyclebin.internal.dto.RecycleBinFilter;
import com.pravoos.ai.recyclebin.internal.model.DeletedEntry;
import com.pravoos.ai.recyclebin.internal.repository.jpa.DeletedEntryRepository;
import com.pravoos.ai.shared.exception.RecycleBinCascadeRestoreException;
import com.pravoos.ai.shared.exception.RecycleBinEntityTypeUnsupportedException;
import com.pravoos.ai.shared.exception.RecycleBinEntryAlreadyRestoredException;
import com.pravoos.ai.shared.exception.RecycleBinEntryNotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecycleBinService implements RecycleBin {

  private static final Logger log = LoggerFactory.getLogger(RecycleBinService.class);
  private static final UUID NIL_ORG_SENTINEL = new UUID(0L, 0L);

  private final DeletedEntryRepository deletedEntryRepository;
  private final RecycleBinProperties properties;
  private final Map<RecycleBinEntityType, SoftDeleteStore> stores;

  public RecycleBinService(
      DeletedEntryRepository deletedEntryRepository,
      RecycleBinProperties properties,
      List<SoftDeleteStore> stores) {
    this.deletedEntryRepository = deletedEntryRepository;
    this.properties = properties;
    this.stores =
        stores.stream()
            .collect(
                Collectors.toMap(
                    SoftDeleteStore::entityType,
                    Function.identity(),
                    (first, second) -> first,
                    () -> new EnumMap<>(RecycleBinEntityType.class)));
  }

  @Override
  @Transactional
  public void moveToBin(RecycleBinEntityType entityType, String entityId, DeletionActor actor) {
    moveToBin(entityType, entityId, actor, true);
  }

  @Override
  @Transactional
  public void moveToBin(
      RecycleBinEntityType entityType, String entityId, DeletionActor actor, boolean cascade) {
    SoftDeleteStore store = requireStore(entityType);
    if (!properties.enabled()) {
      log.info("Recycle bin disabled: purging {} {} immediately", entityType, entityId);
      store.purge(entityId);
      return;
    }
    if (activeEntry(entityType, entityId).isPresent()) {
      log.debug("{} {} is already in the recycle bin", entityType, entityId);
      return;
    }

    BinContents contents = store.moveToBin(entityId, actor, cascade);
    LocalDateTime deletedAt = LocalDateTime.now(ZoneOffset.UTC);
    LocalDateTime purgeAfter = deletedAt.plus(properties.retention());
    UUID cascadeGroupId = UUID.randomUUID();

    List<DeletedEntry> entries = new ArrayList<>();
    entries.add(toEntry(contents.root(), actor, deletedAt, purgeAfter, cascadeGroupId, true));
    for (BinSnapshot cascaded : contents.cascaded()) {
      if (activeEntry(cascaded.entityType(), cascaded.entityId()).isPresent()) {
        continue;
      }
      entries.add(toEntry(cascaded, actor, deletedAt, purgeAfter, cascadeGroupId, false));
    }
    deletedEntryRepository.saveAll(entries);
    log.info(
        "Moved {} {} to recycle bin (group {}, {} cascaded)",
        entityType,
        entityId,
        cascadeGroupId,
        entries.size() - 1);
  }

  @Override
  @Transactional
  public void forgetOwner(UUID ownerId) {
    int removed = deletedEntryRepository.deleteByOwnerId(ownerId);
    if (removed > 0) {
      log.info("Removed {} recycle bin entries owned by {}", removed, ownerId);
    }
  }

  @Transactional(readOnly = true)
  public Page<DeletedEntry> list(DeletionActor actor, RecycleBinFilter filter, Pageable pageable) {
    return deletedEntryRepository.findVisible(
        actor.userId(),
        actor.orgIds().isEmpty() ? List.of(NIL_ORG_SENTINEL) : actor.orgIds(),
        filter.area(),
        filter.deletedByRole(),
        filter.from(),
        filter.to(),
        likePattern(filter.query()),
        pageable);
  }

  @Transactional(readOnly = true)
  public Page<DeletedEntry> listForAdmin(RecycleBinFilter filter, Pageable pageable) {
    return deletedEntryRepository.findForAdmin(
        filter.orgId(),
        filter.area(),
        filter.deletedByRole(),
        filter.from(),
        filter.to(),
        likePattern(filter.query()),
        pageable);
  }

  @Transactional(readOnly = true)
  public List<DeletedEntry> groupItems(UUID entryId, DeletionActor actor) {
    DeletedEntry entry = requireVisible(entryId, actor);
    return deletedEntryRepository.findByCascadeGroupIdAndRestoredAtIsNull(
        entry.getCascadeGroupId());
  }

  @Transactional(readOnly = true)
  public Map<UUID, Long> nestedCounts(List<DeletedEntry> entries) {
    if (entries.isEmpty()) {
      return Map.of();
    }
    List<UUID> cascadeGroupIds = entries.stream().map(DeletedEntry::getCascadeGroupId).toList();
    return deletedEntryRepository.countNestedByCascadeGroupIds(cascadeGroupIds).stream()
        .collect(
            Collectors.toMap(
                DeletedEntryRepository.NestedCountView::getCascadeGroupId,
                DeletedEntryRepository.NestedCountView::getNestedCount));
  }

  @Transactional
  public void restore(UUID entryId, DeletionActor actor) {
    DeletedEntry entry = requireVisible(entryId, actor);
    if (!entry.isCascadeRoot() && hasActiveRoot(entry.getCascadeGroupId())) {
      throw new RecycleBinCascadeRestoreException();
    }

    List<DeletedEntry> group =
        entry.isCascadeRoot()
            ? deletedEntryRepository.findByCascadeGroupIdAndRestoredAtIsNull(
                entry.getCascadeGroupId())
            : List.of(entry);

    LocalDateTime restoredAt = LocalDateTime.now(ZoneOffset.UTC);
    for (DeletedEntry item : group) {
      requireStore(item.getEntityType()).restore(item.getEntityId());
      item.setRestoredAt(restoredAt);
    }
    deletedEntryRepository.saveAll(group);
    log.info(
        "Restored {} recycle bin entries from group {}", group.size(), entry.getCascadeGroupId());
  }

  @Transactional
  public void purgeNow(UUID entryId, DeletionActor actor) {
    DeletedEntry entry = requireVisible(entryId, actor);
    if (!entry.isCascadeRoot()) {
      purge(entry);
      return;
    }
    List<DeletedEntry> group =
        new ArrayList<>(
            deletedEntryRepository.findByCascadeGroupIdAndRestoredAtIsNull(
                entry.getCascadeGroupId()));
    group.sort(Comparator.comparing(DeletedEntry::isCascadeRoot));
    for (DeletedEntry item : group) {
      purge(item);
    }
  }

  @Transactional
  public void purge(DeletedEntry entry) {
    requireStore(entry.getEntityType()).purge(entry.getEntityId());
    deletedEntryRepository.delete(entry);
    log.info("Purged {} {} from recycle bin", entry.getEntityType(), entry.getEntityId());
  }

  @Transactional(readOnly = true)
  public List<DeletedEntry> findExpired(Pageable pageable) {
    return deletedEntryRepository.findExpired(LocalDateTime.now(ZoneOffset.UTC), pageable);
  }

  private boolean hasActiveRoot(UUID cascadeGroupId) {
    return deletedEntryRepository.findByCascadeGroupIdAndRestoredAtIsNull(cascadeGroupId).stream()
        .anyMatch(DeletedEntry::isCascadeRoot);
  }

  private DeletedEntry requireVisible(UUID entryId, DeletionActor actor) {
    DeletedEntry entry =
        deletedEntryRepository
            .findById(entryId)
            .orElseThrow(() -> new RecycleBinEntryNotFoundException(entryId));
    if (entry.getRestoredAt() != null) {
      throw new RecycleBinEntryAlreadyRestoredException(entryId);
    }
    if (actor.isAdmin()) {
      return entry;
    }
    boolean owned = actor.userId().equals(entry.getOwnerId());
    boolean sameOrg = entry.getOrgId() != null && actor.orgIds().contains(entry.getOrgId());
    if (!owned && !sameOrg) {
      throw new RecycleBinEntryNotFoundException(entryId);
    }
    return entry;
  }

  private Optional<DeletedEntry> activeEntry(RecycleBinEntityType entityType, String entityId) {
    return deletedEntryRepository.findByEntityTypeAndEntityIdAndRestoredAtIsNull(
        entityType, entityId);
  }

  private SoftDeleteStore requireStore(RecycleBinEntityType entityType) {
    SoftDeleteStore store = stores.get(entityType);
    if (store == null) {
      throw new RecycleBinEntityTypeUnsupportedException(String.valueOf(entityType));
    }
    return store;
  }

  private DeletedEntry toEntry(
      BinSnapshot snapshot,
      DeletionActor actor,
      LocalDateTime deletedAt,
      LocalDateTime purgeAfter,
      UUID cascadeGroupId,
      boolean cascadeRoot) {
    DeletedEntry entry = new DeletedEntry();
    entry.setEntityType(snapshot.entityType());
    entry.setEntityId(snapshot.entityId());
    entry.setTitle(snapshot.title());
    entry.setArea(snapshot.entityType().area());
    entry.setOwnerId(snapshot.ownerId());
    entry.setOrgId(snapshot.orgId() != null ? snapshot.orgId() : actor.orgId());
    entry.setDeletedBy(actor.userId());
    entry.setDeletedByRole(actor.role());
    entry.setDeletedAt(deletedAt);
    entry.setPurgeAfter(purgeAfter);
    entry.setCascadeGroupId(cascadeGroupId);
    entry.setCascadeRoot(cascadeRoot);
    entry.setPayload(snapshot.payload());
    return entry;
  }

  private static String likePattern(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    String escaped = query.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }
}
