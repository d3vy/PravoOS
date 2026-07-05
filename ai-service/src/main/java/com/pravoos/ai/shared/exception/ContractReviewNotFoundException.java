package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ContractReviewNotFoundException extends PravoosException {

    public ContractReviewNotFoundException(UUID reviewId) {
        super("Ревью договора не найдено: " + reviewId, HttpStatus.NOT_FOUND, "CONTRACT_REVIEW_NOT_FOUND");
    }
}
