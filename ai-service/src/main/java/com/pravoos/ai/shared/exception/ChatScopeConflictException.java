package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class ChatScopeConflictException extends PravoosException {

  public ChatScopeConflictException() {
    super(
        "Chat cannot be scoped to a case and a document at the same time",
        HttpStatus.BAD_REQUEST,
        "CHAT_SCOPE_CONFLICT");
  }
}
