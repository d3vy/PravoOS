package com.pravoos.ai.core.internal.repository.jpa;

import com.pravoos.ai.core.internal.model.entity.AiTrustedTool;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface AiTrustedToolRepository extends JpaRepository<AiTrustedTool, UUID> {

  boolean existsByUserIdAndToolName(UUID userId, String toolName);

  List<AiTrustedTool> findByUserIdOrderByGrantedAtDesc(UUID userId);

  @Modifying
  void deleteByUserIdAndToolName(UUID userId, String toolName);
}
