package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class NoBillableTimeException extends PravoosException {

    public NoBillableTimeException() {
        super("No billable, uninvoiced time entries match the invoice request",
                HttpStatus.UNPROCESSABLE_ENTITY, "NO_BILLABLE_TIME");
    }
}
