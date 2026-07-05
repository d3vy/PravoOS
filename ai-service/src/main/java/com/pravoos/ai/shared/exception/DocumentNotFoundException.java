package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DocumentNotFoundException extends PravoosException {

    public DocumentNotFoundException(UUID id) {
        super("Document not found: " + id, HttpStatus.NOT_FOUND);
    }
}
