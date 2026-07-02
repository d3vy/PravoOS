package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

public class ContractReviewFailedException extends PravoosException {

    public ContractReviewFailedException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "CONTRACT_REVIEW_FAILED");
    }
}
