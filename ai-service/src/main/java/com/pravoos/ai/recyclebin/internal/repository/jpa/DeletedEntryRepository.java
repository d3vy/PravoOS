package com.pravoos.ai.recyclebin.internal.repository.jpa;

import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBinArea;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.recyclebin.internal.model.DeletedEntry;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeletedEntryRepository extends JpaRepository<DeletedEntry, UUID> {

  Optional<DeletedEntry> findByEntityTypeAndEntityIdAndRestoredAtIsNull(
      RecycleBinEntityType entityType, String entityId);

  List<DeletedEntry> findByCascadeGroupIdAndRestoredAtIsNull(UUID cascadeGroupId);

  @Query(
      value =
          """
            SELECT e FROM DeletedEntry e
            WHERE e.restoredAt IS NULL
              AND e.cascadeRoot = TRUE
              AND (e.ownerId = :userId OR e.orgId IN :orgIds)
              AND (:area IS NULL OR e.area = :area)
              AND (:role IS NULL OR e.deletedByRole = :role)
              AND (CAST(:from AS timestamp) IS NULL OR e.deletedAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.deletedAt <= :to)
              AND (:pattern IS NULL OR LOWER(e.title) LIKE :pattern ESCAPE '!')
            ORDER BY e.deletedAt DESC
            """,
      countQuery =
          """
            SELECT COUNT(e) FROM DeletedEntry e
            WHERE e.restoredAt IS NULL
              AND e.cascadeRoot = TRUE
              AND (e.ownerId = :userId OR e.orgId IN :orgIds)
              AND (:area IS NULL OR e.area = :area)
              AND (:role IS NULL OR e.deletedByRole = :role)
              AND (CAST(:from AS timestamp) IS NULL OR e.deletedAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.deletedAt <= :to)
              AND (:pattern IS NULL OR LOWER(e.title) LIKE :pattern ESCAPE '!')
            """)
  Page<DeletedEntry> findVisible(
      @Param("userId") UUID userId,
      @Param("orgIds") Collection<UUID> orgIds,
      @Param("area") RecycleBinArea area,
      @Param("role") DeletionRole role,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("pattern") String pattern,
      Pageable pageable);

  @Query(
      value =
          """
            SELECT e FROM DeletedEntry e
            WHERE e.restoredAt IS NULL
              AND e.cascadeRoot = TRUE
              AND (:orgId IS NULL OR e.orgId = :orgId)
              AND (:area IS NULL OR e.area = :area)
              AND (:role IS NULL OR e.deletedByRole = :role)
              AND (CAST(:from AS timestamp) IS NULL OR e.deletedAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.deletedAt <= :to)
              AND (:pattern IS NULL OR LOWER(e.title) LIKE :pattern ESCAPE '!')
            ORDER BY e.deletedAt DESC
            """,
      countQuery =
          """
            SELECT COUNT(e) FROM DeletedEntry e
            WHERE e.restoredAt IS NULL
              AND e.cascadeRoot = TRUE
              AND (:orgId IS NULL OR e.orgId = :orgId)
              AND (:area IS NULL OR e.area = :area)
              AND (:role IS NULL OR e.deletedByRole = :role)
              AND (CAST(:from AS timestamp) IS NULL OR e.deletedAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.deletedAt <= :to)
              AND (:pattern IS NULL OR LOWER(e.title) LIKE :pattern ESCAPE '!')
            """)
  Page<DeletedEntry> findForAdmin(
      @Param("orgId") UUID orgId,
      @Param("area") RecycleBinArea area,
      @Param("role") DeletionRole role,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("pattern") String pattern,
      Pageable pageable);

  @Query(
      """
        SELECT e FROM DeletedEntry e
        WHERE e.restoredAt IS NULL AND e.purgeAfter <= :now
        ORDER BY e.cascadeRoot ASC, e.purgeAfter ASC
        """)
  List<DeletedEntry> findExpired(@Param("now") LocalDateTime now, Pageable pageable);

  interface NestedCountView {
    UUID getCascadeGroupId();

    long getNestedCount();
  }

  @Query(
      """
        SELECT e.cascadeGroupId AS cascadeGroupId, COUNT(e) AS nestedCount
        FROM DeletedEntry e
        WHERE e.restoredAt IS NULL
          AND e.cascadeRoot = FALSE
          AND e.cascadeGroupId IN :cascadeGroupIds
        GROUP BY e.cascadeGroupId
        """)
  List<NestedCountView> countNestedByCascadeGroupIds(
      @Param("cascadeGroupIds") Collection<UUID> cascadeGroupIds);

  @Modifying
  @Query("DELETE FROM DeletedEntry e WHERE e.ownerId = :ownerId")
  int deleteByOwnerId(@Param("ownerId") UUID ownerId);
}
