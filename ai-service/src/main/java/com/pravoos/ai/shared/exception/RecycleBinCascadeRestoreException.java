package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class RecycleBinCascadeRestoreException extends PravoosException {

  public RecycleBinCascadeRestoreException() {
    super(
        "Эта запись удалена вместе с родительской. Восстановите родительскую запись",
        HttpStatus.CONFLICT);
  }
}
