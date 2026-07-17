package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends PravoosException {

    public EmailAlreadyExistsException() {
        super("Учётная запись с такой почтой уже существует", HttpStatus.CONFLICT, "EMAIL_EXISTS");
    }
}
