package com.pravoos.ai.shared.repository.jpa;

import com.pravoos.ai.shared.model.entity.AccessAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, UUID> {}
