package com.pravoos.ai.practice.internal.repository.jpa;

import com.pravoos.ai.practice.internal.model.entity.EmailAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, UUID> {

  List<EmailAttachment> findByEmailMessageIdOrderByPartIndexAsc(UUID emailMessageId);
}
