package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidPhoneException extends PravoosException {

    public InvalidPhoneException() {
        super("Некорректный телефон. Укажите номер из 11 цифр.", HttpStatus.BAD_REQUEST);
    }
}
