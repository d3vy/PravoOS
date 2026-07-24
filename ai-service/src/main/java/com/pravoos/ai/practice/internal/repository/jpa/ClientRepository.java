package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.Client;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

  List<Client> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId);

  Page<Client> findByLawyerIdOrderByCreatedAtDesc(UUID lawyerId, Pageable pageable);

  int deleteByLawyerId(UUID lawyerId);
}
