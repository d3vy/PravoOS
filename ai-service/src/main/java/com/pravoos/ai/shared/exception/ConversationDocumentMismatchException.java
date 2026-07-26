package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class ConversationDocumentMismatchException extends PravoosException {

  public ConversationDocumentMismatchException(String conversationId) {
    super(
        "Conversation " + conversationId + " belongs to another document",
        HttpStatus.BAD_REQUEST,
        "CONVERSATION_DOCUMENT_MISMATCH");
  }
}
