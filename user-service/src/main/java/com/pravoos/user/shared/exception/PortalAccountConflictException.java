package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class PortalAccountConflictException extends PravoosException {

    public PortalAccountConflictException() {
        super("Учётная запись с этим email не может быть использована для доступа в клиентский портал",
                HttpStatus.CONFLICT);
    }
}
