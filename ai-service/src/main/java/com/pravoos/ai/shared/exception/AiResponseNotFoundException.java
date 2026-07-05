package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AiResponseNotFoundException extends PravoosException {

    public AiResponseNotFoundException(UUID id) {
        super("AI response not found: " + id, HttpStatus.NOT_FOUND);
    }
}
