package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.SavedViewScope;
import com.pravoos.ai.practice.internal.model.entity.SavedView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {

  @Query(
      """
            SELECT v FROM SavedView v
            WHERE v.scope = :scope
              AND (v.lawyerId = :lawyerId
                   OR (v.sharedWithTeam = TRUE AND v.orgId IN :orgIds))
            ORDER BY v.createdAt
            """)
  List<SavedView> findVisible(
      @Param("lawyerId") UUID lawyerId,
      @Param("orgIds") Collection<UUID> orgIds,
      @Param("scope") SavedViewScope scope);

  Optional<SavedView> findByIdAndLawyerId(UUID id, UUID lawyerId);

  boolean existsByLawyerIdAndScopeAndName(UUID lawyerId, SavedViewScope scope, String name);

  int deleteByLawyerId(UUID lawyerId);
}
