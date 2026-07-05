package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class DraftTypeNotFoundException extends PravoosException {

    public DraftTypeNotFoundException(String id) {
        super("Draft type not found: " + id, HttpStatus.BAD_REQUEST);
    }
}
