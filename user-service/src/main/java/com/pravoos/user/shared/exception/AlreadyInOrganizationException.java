package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class AlreadyInOrganizationException extends PravoosException {

    public AlreadyInOrganizationException() {
        super("Вы уже состоите в организации", HttpStatus.CONFLICT, "ALREADY_IN_ORG");
    }
}
