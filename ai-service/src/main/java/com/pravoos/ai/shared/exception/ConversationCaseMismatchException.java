package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class ConversationCaseMismatchException extends PravoosException {

  public ConversationCaseMismatchException(String conversationId) {
    super(
        "Conversation " + conversationId + " belongs to another case",
        HttpStatus.BAD_REQUEST,
        "CONVERSATION_CASE_MISMATCH");
  }
}
