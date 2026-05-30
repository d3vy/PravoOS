package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

public class ConversationNotFoundException extends PravoosException {

    public ConversationNotFoundException(String id) {
        super("Conversation not found: " + id, HttpStatus.NOT_FOUND);
    }
}
