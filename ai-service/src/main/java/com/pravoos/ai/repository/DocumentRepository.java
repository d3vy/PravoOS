package com.pravoos.ai.repository;

import com.pravoos.ai.model.entity.Document;
import com.pravoos.ai.model.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOrderByUploadedAtDesc();

    List<Document> findByStatus(DocumentStatus status);
}
