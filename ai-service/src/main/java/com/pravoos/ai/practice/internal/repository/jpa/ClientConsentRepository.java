package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.ClientConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientConsentRepository extends JpaRepository<ClientConsent, UUID> {

  Optional<ClientConsent> findFirstByClientIdOrderByGrantedAtDesc(UUID clientId);

  List<ClientConsent> findByClientIdOrderByGrantedAtDesc(UUID clientId);

  int deleteByClientId(UUID clientId);
}
