package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ClientNotFoundException extends PravoosException {

    public ClientNotFoundException(UUID id) {
        super("Client not found: " + id, HttpStatus.NOT_FOUND);
    }
}
