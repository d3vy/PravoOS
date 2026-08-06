package com.pravoos.ai.shared.exception;

public class TrustedCaStoreException extends RuntimeException {

  public TrustedCaStoreException(String reason) {
    super("Доверенный список УЦ не загружен: " + reason);
  }

  public TrustedCaStoreException(String reason, Throwable cause) {
    super("Доверенный список УЦ не загружен: " + reason, cause);
  }
}
