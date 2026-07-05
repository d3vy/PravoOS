package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    List<DocumentTemplate> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    Optional<DocumentTemplate> findByIdAndLawyerId(UUID id, UUID lawyerId);

    int deleteByLawyerId(UUID lawyerId);
}
