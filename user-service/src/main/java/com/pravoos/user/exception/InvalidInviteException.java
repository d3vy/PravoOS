package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidInviteException extends PravoosException {

    public InvalidInviteException() {
        super("Приглашение недействительно или истекло", HttpStatus.BAD_REQUEST, "INVALID_INVITE");
    }
}
