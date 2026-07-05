package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidPasswordResetTokenException extends PravoosException {

    public InvalidPasswordResetTokenException() {
        super("Ссылка для сброса пароля недействительна или истекла", HttpStatus.BAD_REQUEST);
    }
}
