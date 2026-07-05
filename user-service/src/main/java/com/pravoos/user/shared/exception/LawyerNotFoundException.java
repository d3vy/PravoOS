package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class LawyerNotFoundException extends PravoosException {

    public LawyerNotFoundException() {
        super("Юрист не найден", HttpStatus.NOT_FOUND);
    }
}
