package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.CaseDraftVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseDraftVersionRepository extends JpaRepository<CaseDraftVersion, UUID> {

  List<CaseDraftVersion> findByDraftIdOrderByVersionNoDesc(UUID draftId);

  Optional<CaseDraftVersion> findByIdAndDraftId(UUID id, UUID draftId);

  @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM CaseDraftVersion v WHERE v.draftId = :draftId")
  int maxVersionNo(@Param("draftId") UUID draftId);
}
