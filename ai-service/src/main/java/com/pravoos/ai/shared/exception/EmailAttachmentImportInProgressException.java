package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class EmailAttachmentImportInProgressException extends PravoosException {

  public EmailAttachmentImportInProgressException(UUID emailId) {
    super(
        "Импорт вложений письма " + emailId + " уже выполняется — дождитесь его завершения",
        HttpStatus.CONFLICT,
        "EMAIL_ATTACHMENT_IMPORT_IN_PROGRESS");
  }
}
