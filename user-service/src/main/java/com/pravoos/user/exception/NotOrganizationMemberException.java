package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class NotOrganizationMemberException extends PravoosException {

    public NotOrganizationMemberException() {
        super("Вы не состоите в организации", HttpStatus.FORBIDDEN, "NOT_ORG_MEMBER");
    }
}
