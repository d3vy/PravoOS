package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaseMessageRepository extends JpaRepository<CaseMessage, UUID> {

    List<CaseMessage> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
