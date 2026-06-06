package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationTokenException extends PravoosException {

    public InvalidVerificationTokenException() {
        super("Ссылка для подтверждения недействительна или истекла", HttpStatus.BAD_REQUEST);
    }
}
