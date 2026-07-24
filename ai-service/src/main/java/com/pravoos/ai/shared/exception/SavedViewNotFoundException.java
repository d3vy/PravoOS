package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class SavedViewNotFoundException extends PravoosException {

  public SavedViewNotFoundException(UUID id) {
    super("Сохранённый вид не найден: " + id, HttpStatus.NOT_FOUND);
  }
}
