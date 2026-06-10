package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class ApplicationTokenNotFoundException extends PravoosException {

    public ApplicationTokenNotFoundException() {
        super("Заявка не найдена. Проверьте ссылку.", HttpStatus.NOT_FOUND);
    }
}
