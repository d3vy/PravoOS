package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

public class StorageQuotaExceededException extends PravoosException {

    public StorageQuotaExceededException(String message) {
        super(message, HttpStatus.INSUFFICIENT_STORAGE, "STORAGE_QUOTA_EXCEEDED");
    }
}
