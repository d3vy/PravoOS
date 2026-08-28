package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecycleBinEntryNotFoundException extends PravoosException {

  public RecycleBinEntryNotFoundException(UUID entryId) {
    super("Запись корзины не найдена: " + entryId, HttpStatus.NOT_FOUND);
  }
}
