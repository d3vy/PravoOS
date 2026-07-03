package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class OrganizationNotFoundException extends PravoosException {

    public OrganizationNotFoundException() {
        super("Организация не найдена", HttpStatus.NOT_FOUND, "ORG_NOT_FOUND");
    }
}
