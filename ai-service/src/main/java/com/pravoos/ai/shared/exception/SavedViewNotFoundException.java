package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SavedViewNotFoundException extends PravoosException {

    public SavedViewNotFoundException(UUID id) {
        super("Сохранённый вид не найден: " + id, HttpStatus.NOT_FOUND);
    }
}
