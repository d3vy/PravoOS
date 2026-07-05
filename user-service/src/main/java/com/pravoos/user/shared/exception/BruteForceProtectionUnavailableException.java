package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class BruteForceProtectionUnavailableException extends PravoosException {

    public BruteForceProtectionUnavailableException() {
        super("Сервис временно недоступен, попробуйте позже", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
