package com.pravoos.user.exception;

import org.springframework.http.HttpStatus;

public class ProfileNotFoundException extends PravoosException {

    public ProfileNotFoundException() {
        super("Профиль юриста не найден", HttpStatus.NOT_FOUND);
    }
}
