package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.PendingLawyerPurge;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PendingLawyerPurgeRepository extends JpaRepository<PendingLawyerPurge, UUID> {

    @Query("SELECT p FROM PendingLawyerPurge p ORDER BY p.createdAt ASC")
    List<PendingLawyerPurge> findOldestBatch(Pageable pageable);
}
