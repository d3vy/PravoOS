package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

    int deleteByLawyerId(UUID lawyerId);
}
