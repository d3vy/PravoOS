package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.Document;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOrderByUploadedAtDesc();

    List<Document> findByStatus(DocumentStatus status);

    List<Document> findByCaseIdIsNullOrderByUploadedAtDesc();

    Page<Document> findByCaseIdIsNullOrderByUploadedAtDesc(Pageable pageable);

    List<Document> findByCaseIdOrderByUploadedAtDesc(UUID caseId);

    List<Document> findByCaseIdAndVisibleToClientTrueOrderByUploadedAtDesc(UUID caseId);

    long countByCaseId(UUID caseId);

    long countByUploadedBy(UUID uploadedBy);

    @Query("SELECT COALESCE(SUM(d.sizeBytes), 0) FROM Document d WHERE d.uploadedBy = :uploadedBy")
    long sumSizeBytesByUploadedBy(@Param("uploadedBy") UUID uploadedBy);

    @Query("""
            SELECT d FROM Document d
            WHERE d.caseId IS NOT NULL
              AND EXISTS (SELECT 1 FROM Case c WHERE c.id = d.caseId AND c.lawyerId = :lawyerId)
              AND (LOWER(d.title) LIKE :pattern ESCAPE '\\'
                   OR (:searchContent = TRUE AND EXISTS (SELECT 1 FROM DocumentChunk ch
                              WHERE ch.document.id = d.id AND LOWER(ch.content) LIKE :pattern ESCAPE '\\')))
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> searchOwnedByLawyer(@Param("lawyerId") UUID lawyerId,
                                       @Param("pattern") String pattern,
                                       @Param("searchContent") boolean searchContent,
                                       Pageable pageable);
}
