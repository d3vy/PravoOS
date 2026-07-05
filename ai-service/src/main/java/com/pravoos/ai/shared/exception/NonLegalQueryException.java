package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class NonLegalQueryException extends PravoosException {

    public NonLegalQueryException() {
        super(
            "Кажется, ваш запрос не связан с юриспруденцией. " +
            "Я помогаю только с юридическими вопросами — попробуйте сформулировать запрос иначе.",
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
