package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.ClientConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientConsentRepository extends JpaRepository<ClientConsent, UUID> {

    Optional<ClientConsent> findFirstByClientIdOrderByGrantedAtDesc(UUID clientId);

    List<ClientConsent> findByClientIdOrderByGrantedAtDesc(UUID clientId);

    int deleteByClientId(UUID clientId);
}
