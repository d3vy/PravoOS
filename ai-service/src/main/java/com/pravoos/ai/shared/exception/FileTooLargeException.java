package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class FileTooLargeException extends PravoosException {

    public FileTooLargeException(long maxBytes) {
        super("Файл превышает максимальный размер (" + maxBytes / (1024 * 1024) + " МБ)",
                HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
    }
}
