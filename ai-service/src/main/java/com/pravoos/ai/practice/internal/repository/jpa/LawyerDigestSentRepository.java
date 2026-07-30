package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.LawyerDigestSent;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LawyerDigestSentRepository extends JpaRepository<LawyerDigestSent, UUID> {

  boolean existsByLawyerIdAndDigestDate(UUID lawyerId, LocalDate digestDate);
}
