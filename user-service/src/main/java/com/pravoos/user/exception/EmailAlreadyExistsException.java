package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends PravoosException {

    public EmailAlreadyExistsException(String email) {
        super("Учётная запись с такой почтой уже существует", HttpStatus.CONFLICT, "EMAIL_EXISTS");
    }
}
