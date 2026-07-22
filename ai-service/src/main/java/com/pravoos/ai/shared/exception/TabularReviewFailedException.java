package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class TabularReviewFailedException extends PravoosException {

    public TabularReviewFailedException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "TABULAR_REVIEW_FAILED");
    }
}
