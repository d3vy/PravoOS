package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    @Query("""
            SELECT d FROM WorkflowDefinition d
            WHERE d.system = TRUE OR d.createdBy = :lawyerId OR d.orgId IN :orgIds
            ORDER BY d.system DESC, d.category ASC, d.name ASC
            """)
    List<WorkflowDefinition> findVisible(@Param("lawyerId") UUID lawyerId, @Param("orgIds") List<UUID> orgIds);
}
