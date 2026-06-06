package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class ApplicationAlreadyExistsException extends PravoosException {

    public ApplicationAlreadyExistsException(String email) {
        super("Заявка с такой почтой уже находится на рассмотрении", HttpStatus.CONFLICT);
    }
}
