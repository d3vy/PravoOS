package com.pravoos.user.privacy.internal.repository;

import com.pravoos.user.privacy.internal.model.entity.UserConsent;
import com.pravoos.user.privacy.internal.model.enums.ConsentPurpose;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {

  List<UserConsent> findByUserIdOrderByGrantedAtDesc(UUID userId);

  Optional<UserConsent> findByUserIdAndPurposeAndRevokedAtIsNull(
      UUID userId, ConsentPurpose purpose);

  List<UserConsent> findByUserIdAndRevokedAtIsNull(UUID userId);
}
