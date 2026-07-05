package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class MessageNotFoundException extends PravoosException {

    public MessageNotFoundException(String id) {
        super("Message not found: " + id, HttpStatus.NOT_FOUND);
    }
}
