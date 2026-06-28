package com.pravoos.ai.repository.jpa;

import com.pravoos.ai.model.entity.ClientContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ClientContactRepository extends JpaRepository<ClientContact, UUID> {

    List<ClientContact> findByClientIdOrderByContactDateDescCreatedAtDesc(UUID clientId);

    Page<ClientContact> findByClientIdOrderByContactDateDescCreatedAtDesc(UUID clientId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ClientContact c WHERE c.clientId IN "
            + "(SELECT cl.id FROM Client cl WHERE cl.lawyerId = :lawyerId)")
    int deleteByLawyerId(@Param("lawyerId") UUID lawyerId);
}
