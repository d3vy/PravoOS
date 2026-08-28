package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class RecycleBinEntityTypeUnsupportedException extends PravoosException {

  public RecycleBinEntityTypeUnsupportedException(String entityType) {
    super("Корзина не поддерживает тип: " + entityType, HttpStatus.BAD_REQUEST);
  }
}
