package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.recyclebin.api.LeafSoftDeleteRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRepository extends LeafSoftDeleteRepository<WorkflowDefinition> {

  @Modifying
  @Query("DELETE FROM WorkflowDefinition d WHERE d.createdBy = :lawyerId AND d.system = FALSE")
  int deleteByCreatedByLawyer(@Param("lawyerId") UUID lawyerId);

  @Query(
      """
            SELECT d FROM WorkflowDefinition d
            WHERE d.system = TRUE OR d.createdBy = :lawyerId OR d.orgId IN :orgIds
            ORDER BY d.system DESC, d.category ASC, d.name ASC
            """)
  List<WorkflowDefinition> findVisible(
      @Param("lawyerId") UUID lawyerId, @Param("orgIds") List<UUID> orgIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE workflow_definitions SET deleted_at = :deletedAt "
              + "WHERE id = :id AND deleted_at IS NULL",
      nativeQuery = true)
  int softDelete(@Param("id") UUID id, @Param("deletedAt") LocalDateTime deletedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE workflow_definitions SET deleted_at = NULL WHERE id = :id",
      nativeQuery = true)
  int restore(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM workflow_definitions WHERE id = :id", nativeQuery = true)
  int hardDelete(@Param("id") UUID id);
}
