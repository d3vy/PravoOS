package com.pravoos.ai.document.internal.repository.jpa;

import com.pravoos.ai.document.internal.model.entity.DocumentChunk;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

  @Modifying
  @Query("DELETE FROM DocumentChunk dc WHERE dc.document.id = :documentId")
  void deleteByDocumentId(@Param("documentId") UUID documentId);

  @Query(
      "SELECT dc.content FROM DocumentChunk dc WHERE dc.document.id IN :documentIds ORDER BY dc.document.id ASC, dc.chunkIndex ASC")
  List<String> findContentByDocumentIdIn(@Param("documentIds") Collection<UUID> documentIds);

  @Query(
      """
            SELECT dc.content FROM DocumentChunk dc
            WHERE dc.document.id = :documentId AND LOWER(dc.content) LIKE :pattern ESCAPE '!'
            ORDER BY dc.chunkIndex ASC
            """)
  List<String> findMatchingContent(
      @Param("documentId") UUID documentId, @Param("pattern") String pattern, Pageable pageable);

  @Query(
      value =
          """
            SELECT EXISTS (
                SELECT 1 FROM document_chunks dc
                JOIN documents d ON d.id = dc.document_id
                WHERE d.case_id IS NULL AND d.document_kind <> 'CHAT_ATTACHMENT'
                  AND dc.content LIKE :pattern ESCAPE '!'
            )
            """,
      nativeQuery = true)
  boolean existsInKnowledgeBaseByContent(@Param("pattern") String pattern);
}
