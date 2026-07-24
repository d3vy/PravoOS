package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.SignatureRequest;
import com.pravoos.ai.shared.model.enums.SignatureStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignatureRequestRepository extends JpaRepository<SignatureRequest, UUID> {

  List<SignatureRequest> findByCaseIdOrderByCreatedAtDesc(UUID caseId);

  List<SignatureRequest> findBySignerClientIdInAndStatusOrderByCreatedAtDesc(
      List<UUID> signerClientIds, SignatureStatus status);

  Optional<SignatureRequest> findByDocumentIdAndSignerClientIdAndStatus(
      UUID documentId, UUID signerClientId, SignatureStatus status);

  List<SignatureRequest> findByStatusAndExpiresAtBefore(
      SignatureStatus status, LocalDateTime moment);

  void deleteByCaseId(UUID caseId);

  @Modifying
  @Query("DELETE FROM SignatureRequest s WHERE s.requestedBy = :lawyerId")
  int deleteByRequestedBy(@Param("lawyerId") UUID lawyerId);
}
