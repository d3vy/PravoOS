package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends PravoosException {

    public EmailNotVerifiedException() {
        super("Заявка не может быть одобрена: email не подтверждён", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
