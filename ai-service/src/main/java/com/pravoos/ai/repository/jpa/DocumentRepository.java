package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.Document;
import com.pravoos.ai.model.enums.DocumentStatus;
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

    List<Document> findByCaseIdOrderByUploadedAtDesc(UUID caseId);

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
