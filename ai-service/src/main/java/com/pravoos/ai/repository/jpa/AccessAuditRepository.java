package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.AccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, UUID> {
}
