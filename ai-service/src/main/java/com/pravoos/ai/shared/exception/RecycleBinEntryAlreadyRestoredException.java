package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class RecycleBinEntryAlreadyRestoredException extends PravoosException {

  public RecycleBinEntryAlreadyRestoredException(UUID entryId) {
    super("Запись корзины уже восстановлена: " + entryId, HttpStatus.CONFLICT);
  }
}
