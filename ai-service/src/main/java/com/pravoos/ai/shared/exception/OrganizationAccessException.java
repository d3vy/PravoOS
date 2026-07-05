package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class OrganizationAccessException extends PravoosException {

    public OrganizationAccessException(UUID orgId) {
        super("Нет доступа к организации: " + orgId, HttpStatus.FORBIDDEN);
    }
}
