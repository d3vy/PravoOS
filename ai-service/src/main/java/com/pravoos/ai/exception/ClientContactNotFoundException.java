package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ClientContactNotFoundException extends PravoosException {

    public ClientContactNotFoundException(UUID id) {
        super("Client contact not found: " + id, HttpStatus.NOT_FOUND);
    }
}
