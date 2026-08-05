package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class EmailMessageUnavailableException extends PravoosException {

  public EmailMessageUnavailableException(long uid) {
    super(
        "Письмо больше недоступно в почтовом ящике (uid " + uid + ")",
        HttpStatus.CONFLICT,
        "EMAIL_MESSAGE_UNAVAILABLE");
  }
}
