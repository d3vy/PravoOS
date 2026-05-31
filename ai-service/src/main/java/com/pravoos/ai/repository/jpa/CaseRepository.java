package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.Case;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    List<Case> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);
}
