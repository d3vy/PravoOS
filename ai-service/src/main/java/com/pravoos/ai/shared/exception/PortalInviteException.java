package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class PortalInviteException extends PravoosException {

    public PortalInviteException(UUID clientId) {
        super("Failed to create portal invite for client: " + clientId, HttpStatus.BAD_GATEWAY);
    }
}
