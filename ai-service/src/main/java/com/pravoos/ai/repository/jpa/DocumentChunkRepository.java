package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    @Modifying
    @Query("DELETE FROM DocumentChunk dc WHERE dc.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    @Query("SELECT dc.content FROM DocumentChunk dc WHERE dc.document.id IN :documentIds ORDER BY dc.chunkIndex ASC")
    List<String> findContentByDocumentIdIn(@Param("documentIds") Collection<UUID> documentIds);
}
