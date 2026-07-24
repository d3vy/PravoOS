package com.pravoos.common.exception;

public class InvalidPhoneNumberException extends RuntimeException {

  public InvalidPhoneNumberException() {
    super("Некорректный телефон. Укажите номер из 11 цифр.");
  }
}
