package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.ClientContact;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientContactRepository extends JpaRepository<ClientContact, UUID> {

  List<ClientContact> findByClientIdOrderByContactDateDescCreatedAtDesc(UUID clientId);

  Page<ClientContact> findByClientIdOrderByContactDateDescCreatedAtDesc(
      UUID clientId, Pageable pageable);

  @Modifying
  @Query(
      "DELETE FROM ClientContact c WHERE c.clientId IN "
          + "(SELECT cl.id FROM Client cl WHERE cl.lawyerId = :lawyerId)")
  int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
