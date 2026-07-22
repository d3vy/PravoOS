package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class UnsafeFileContentException extends PravoosException {

    public UnsafeFileContentException(String reason) {
        super("Файл отклонён: " + reason, HttpStatus.UNPROCESSABLE_ENTITY, "UNSAFE_FILE_CONTENT");
    }
}
