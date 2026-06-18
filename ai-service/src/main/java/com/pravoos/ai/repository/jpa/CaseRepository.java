package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.enums.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    List<Case> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    List<Case> findByLawyerIdAndStatusOrderByCreatedAtDesc(UUID lawyerId, CaseStatus status);

    List<Case> findByClientIdAndLawyerIdOrderByCreatedAtDesc(UUID clientId, UUID lawyerId);

    int deleteByLawyerId(UUID lawyerId);
}
