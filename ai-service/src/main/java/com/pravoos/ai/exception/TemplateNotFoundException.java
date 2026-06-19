package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class TemplateNotFoundException extends PravoosException {

    public TemplateNotFoundException(UUID id) {
        super("Template not found: " + id, HttpStatus.NOT_FOUND);
    }
}
