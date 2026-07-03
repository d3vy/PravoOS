package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class OrganizationAccessDeniedException extends PravoosException {

    public OrganizationAccessDeniedException() {
        super("Недостаточно прав в организации", HttpStatus.FORBIDDEN, "ORG_ACCESS_DENIED");
    }
}
