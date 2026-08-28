package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class DocumentAlreadyLinkedException extends PravoosException {

  public DocumentAlreadyLinkedException(UUID documentId, UUID caseId) {
    super("Document " + documentId + " is already linked to case " + caseId, HttpStatus.CONFLICT);
  }
}
