package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class SavedViewNameTakenException extends PravoosException {

    public SavedViewNameTakenException(String name) {
        super("Вид с таким названием уже существует: " + name, HttpStatus.CONFLICT, "SAVED_VIEW_NAME_TAKEN");
    }
}
