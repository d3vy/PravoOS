package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidTelegramLinkCodeException extends PravoosException {

    public InvalidTelegramLinkCodeException() {
        super("Код привязки Telegram недействителен или истёк", HttpStatus.NOT_FOUND);
    }
}
