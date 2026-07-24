package com.pravoos.ai.shared.exception;

import org.springframework.http.HttpStatus;

public class SavedViewOrgRequiredException extends PravoosException {

  public SavedViewOrgRequiredException() {
    super(
        "Укажите организацию: юрист состоит в нескольких организациях",
        HttpStatus.BAD_REQUEST,
        "SAVED_VIEW_ORG_REQUIRED");
  }
}
