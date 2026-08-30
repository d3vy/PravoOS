package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.DocumentTemplate;
import com.pravoos.ai.recyclebin.api.LeafSoftDeleteRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTemplateRepository extends LeafSoftDeleteRepository<DocumentTemplate> {

  List<DocumentTemplate> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

  Optional<DocumentTemplate> findByIdAndLawyerId(UUID id, UUID lawyerId);

  int deleteByLawyerId(UUID lawyerId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE document_templates SET deleted_at = :deletedAt"
              + " WHERE id = :id AND deleted_at IS NULL",
      nativeQuery = true)
  int softDelete(@Param("id") UUID id, @Param("deletedAt") LocalDateTime deletedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE document_templates SET deleted_at = NULL WHERE id = :id",
      nativeQuery = true)
  int restore(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM document_templates WHERE id = :id", nativeQuery = true)
  int hardDelete(@Param("id") UUID id);
}
