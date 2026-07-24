package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.document.api.DocumentUploadResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChatAttachmentService {

  private static final Logger log = LoggerFactory.getLogger(ChatAttachmentService.class);

  private final DocumentCommand documentCommand;
  private final DocumentAccess documentAccess;

  public ChatAttachmentService(DocumentCommand documentCommand, DocumentAccess documentAccess) {
    this.documentCommand = documentCommand;
    this.documentAccess = documentAccess;
  }

  public DocumentUploadResponse upload(MultipartFile file, String title, UUID lawyerId) {
    DocumentUploadResponse uploaded = documentCommand.uploadChatAttachment(file, title, lawyerId);
    log.info("Chat attachment {} uploaded by lawyer {}", uploaded.id(), lawyerId);
    return uploaded;
  }

  public List<DocumentResponse> findAll(UUID lawyerId) {
    return documentAccess.findChatAttachments(lawyerId);
  }
}
