package com.pravoos.ai.exception;

import org.springframework.http.HttpStatus;

public class NonLegalQueryException extends PravoosException {

    public NonLegalQueryException() {
        super(
            "Я специализируюсь исключительно на юридических вопросах. " +
            "Пожалуйста, задайте вопрос, связанный с правом, законодательством или юридической практикой.",
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
