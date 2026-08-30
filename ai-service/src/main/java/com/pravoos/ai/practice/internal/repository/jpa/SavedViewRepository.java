package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.SavedView;
import com.pravoos.ai.recyclebin.api.LeafSoftDeleteRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedViewRepository extends LeafSoftDeleteRepository<SavedView> {

  @Query(
      """
            SELECT v FROM SavedView v
            WHERE v.scope = :scope
              AND (v.lawyerId = :lawyerId
                   OR (v.sharedWithTeam = TRUE AND v.orgId IN :orgIds))
            ORDER BY v.createdAt
            """)
  List<SavedView> findVisible(
      @Param("lawyerId") UUID lawyerId,
      @Param("orgIds") Collection<UUID> orgIds,
      @Param("scope") SavedViewScope scope);

  Optional<SavedView> findByIdAndLawyerId(UUID id, UUID lawyerId);

  boolean existsByLawyerIdAndScopeAndName(UUID lawyerId, SavedViewScope scope, String name);

  int deleteByLawyerId(UUID lawyerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE saved_views SET deleted_at = :deletedAt WHERE id = :id AND deleted_at IS NULL",
      nativeQuery = true)
  int softDelete(@Param("id") UUID id, @Param("deletedAt") LocalDateTime deletedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE saved_views SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
  int restore(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM saved_views WHERE id = :id", nativeQuery = true)
  int hardDelete(@Param("id") UUID id);
}
