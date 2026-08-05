package com.pravoos.user.shared.exception;

import org.springframework.http.HttpStatus;

public class SubjectNotFoundException extends PravoosException {

  public SubjectNotFoundException() {
    super("Субъект персональных данных не найден", HttpStatus.NOT_FOUND, "SUBJECT_NOT_FOUND");
  }
}
