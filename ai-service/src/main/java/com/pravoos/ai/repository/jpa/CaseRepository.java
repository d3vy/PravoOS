package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.Case;
import com.pravoos.ai.model.enums.CaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, UUID> {

    List<Case> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    List<Case> findByLawyerIdAndStatusOrderByCreatedAtDesc(UUID lawyerId, CaseStatus status);

    List<Case> findByClientIdAndLawyerIdOrderByCreatedAtDesc(UUID clientId, UUID lawyerId);

    List<Case> findByFilingDeadline(LocalDate filingDeadline);

    List<Case> findByNextHearingDate(LocalDate nextHearingDate);

    List<Case> findByExpiresAt(LocalDate expiresAt);

    int deleteByLawyerId(UUID lawyerId);
}
