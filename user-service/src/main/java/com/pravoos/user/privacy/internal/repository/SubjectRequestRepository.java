package com.pravoos.user.privacy.internal.repository;

import com.pravoos.user.privacy.internal.model.entity.SubjectRequest;
import com.pravoos.user.privacy.internal.model.enums.SubjectRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRequestRepository extends JpaRepository<SubjectRequest, UUID> {

  List<SubjectRequest> findByUserIdOrderByRequestedAtDesc(UUID userId);

  List<SubjectRequest> findByStatusOrderByDueAtAsc(SubjectRequestStatus status);
}
