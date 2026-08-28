package com.pravoos.ai.document.internal.repository.jpa;

import com.pravoos.ai.document.internal.model.entity.Document;
import com.pravoos.ai.shared.model.enums.DocumentKind;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  List<Document> findAllByOrderByUploadedAtDesc();

  Optional<Document> findByDocumentKindAndActCanonicalAndArticleNumberAndSupersededFalse(
      DocumentKind documentKind, String actCanonical, String articleNumber);

  Optional<Document>
      findFirstByDocumentKindAndActCanonicalAndArticleNumberAndSupersededTrueOrderByEditionDateDesc(
          DocumentKind documentKind, String actCanonical, String articleNumber);

  List<Document> findByDocumentKindAndArticleNumberAndSupersededTrueOrderByEditionDateDesc(
      DocumentKind documentKind, String articleNumber);

  List<Document> findByDocumentKindAndArticleNumberAndSupersededFalse(
      DocumentKind documentKind, String articleNumber);

  Page<Document> findByDocumentKindAndSupersededFalseOrderByEditionDateDesc(
      DocumentKind documentKind, Pageable pageable);

  List<Document> findByStatus(DocumentStatus status);

  List<Document> findByCaseIdIsNullOrderByUploadedAtDesc();

  Page<Document> findByCaseIdIsNullAndDocumentKindNotOrderByUploadedAtDesc(
      DocumentKind documentKind, Pageable pageable);

  List<Document> findByUploadedByAndDocumentKindOrderByUploadedAtDesc(
      UUID uploadedBy, DocumentKind documentKind);

  List<Document> findByCaseIdOrderByUploadedAtDesc(UUID caseId);

  List<Document> findByCaseIdAndVisibleToClientTrueOrderByUploadedAtDesc(UUID caseId);

  long countByCaseId(UUID caseId);

  long countByUploadedBy(UUID uploadedBy);

  @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM Document d WHERE d.uploadedBy = :uploadedBy")
  long sumSizeBytesByUploadedBy(@Param("uploadedBy") UUID uploadedBy);

  @Query(
      """
            SELECT d FROM Document d
            WHERE d.caseId IS NOT NULL
              AND EXISTS (SELECT 1 FROM Case c WHERE c.id = d.caseId AND c.lawyerId = :lawyerId)
              AND (LOWER(d.title) LIKE :pattern ESCAPE '!'
                   OR (:searchContent = TRUE AND EXISTS (SELECT 1 FROM DocumentChunk ch
                              WHERE ch.document.id = d.id AND LOWER(ch.content) LIKE :pattern ESCAPE '!')))
            ORDER BY d.uploadedAt DESC
            """)
  List<Document> searchOwnedByLawyer(
      @Param("lawyerId") UUID lawyerId,
      @Param("pattern") String pattern,
      @Param("searchContent") boolean searchContent,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value = "UPDATE documents SET deleted_at = :deletedAt WHERE id = :id AND deleted_at IS NULL",
      nativeQuery = true)
  int softDelete(@Param("id") UUID id, @Param("deletedAt") LocalDateTime deletedAt);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "UPDATE documents SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
  int restore(@Param("id") UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = "DELETE FROM documents WHERE id = :id", nativeQuery = true)
  int hardDelete(@Param("id") UUID id);

  @Query(value = "SELECT file_path FROM documents WHERE id = :id", nativeQuery = true)
  Optional<String> findFilePathIncludingDeleted(@Param("id") UUID id);

  @Query(value = "SELECT id FROM documents WHERE case_id = :caseId", nativeQuery = true)
  List<UUID> findIdsByCaseIdIncludingDeleted(@Param("caseId") UUID caseId);
}
