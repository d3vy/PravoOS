package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

public class InvalidPhoneException extends PravoosException {

    public InvalidPhoneException() {
        super("Invalid phone number", HttpStatus.BAD_REQUEST);
    }
}
