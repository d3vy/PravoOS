package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.CaseDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseDraftRepository extends JpaRepository<CaseDraft, UUID> {

    List<CaseDraft> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

    Optional<CaseDraft> findByIdAndLawyerId(UUID id, UUID lawyerId);

    int deleteByLawyerId(UUID lawyerId);
}
