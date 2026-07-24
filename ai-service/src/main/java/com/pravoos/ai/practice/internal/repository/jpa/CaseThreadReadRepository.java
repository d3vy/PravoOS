package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseThreadRead;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseThreadReadRepository
    extends JpaRepository<CaseThreadRead, CaseThreadRead.Id> {}
