package com.pravoos.ai.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class ClientHasIssuedInvoicesException extends PravoosException {

  public ClientHasIssuedInvoicesException(UUID clientId, long invoiceCount) {
    super(
        "У клиента "
            + clientId
            + " есть выставленные счета ("
            + invoiceCount
            + " шт.) — удаление уничтожит бухгалтерские документы. "
            + "Сначала отмените счета или оставьте клиента в архиве.",
        HttpStatus.CONFLICT,
        "CLIENT_HAS_ISSUED_INVOICES");
  }
}
