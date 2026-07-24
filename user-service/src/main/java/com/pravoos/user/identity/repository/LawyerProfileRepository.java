package com.pravoos.user.identity.repository;

import com.pravoos.user.identity.model.entity.LawyerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LawyerProfileRepository extends JpaRepository<LawyerProfile, UUID> {

  @Query("SELECT lp FROM LawyerProfile lp JOIN FETCH lp.user WHERE lp.userId = :userId")
  Optional<LawyerProfile> findByUserIdWithUser(@Param("userId") UUID userId);
}
