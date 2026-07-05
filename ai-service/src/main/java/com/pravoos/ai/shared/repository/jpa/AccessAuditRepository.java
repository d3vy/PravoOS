package com.pravoos.ai.shared.repository.jpa;

import com.pravoos.ai.shared.model.entity.AccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, UUID> {
}
