package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ClientEmailRequiredException extends PravoosException {

    public ClientEmailRequiredException(UUID clientId) {
        super("Client has no email, cannot invite to portal: " + clientId, HttpStatus.BAD_REQUEST);
    }
}
