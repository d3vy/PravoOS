package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class BarNumberAlreadyExistsException extends PravoosException {

    public BarNumberAlreadyExistsException(String barNumber) {
        super("Адвокат с таким номером удостоверения уже зарегистрирован или находится на рассмотрении", HttpStatus.CONFLICT);
    }
}
