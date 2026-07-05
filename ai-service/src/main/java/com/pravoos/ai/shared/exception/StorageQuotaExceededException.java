package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class StorageQuotaExceededException extends PravoosException {

    public StorageQuotaExceededException(String message) {
        super(message, HttpStatus.INSUFFICIENT_STORAGE, "STORAGE_QUOTA_EXCEEDED");
    }
}
