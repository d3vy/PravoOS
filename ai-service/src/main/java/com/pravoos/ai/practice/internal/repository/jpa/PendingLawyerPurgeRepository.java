package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.PendingLawyerPurge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PendingLawyerPurgeRepository extends JpaRepository<PendingLawyerPurge, UUID> {

  @Query("SELECT p FROM PendingLawyerPurge p ORDER BY p.createdAt ASC")
  List<PendingLawyerPurge> findOldestBatch(Pageable pageable);
}
