package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class InvalidSignatureFileException extends PravoosException {

    public InvalidSignatureFileException(String reason) {
        super("Файл подписи отклонён: " + reason, HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SIGNATURE_FILE");
    }
}
