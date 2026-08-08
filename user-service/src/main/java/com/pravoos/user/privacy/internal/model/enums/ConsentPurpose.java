package com.pravoos.user.privacy.internal.model.enums;

public enum ConsentPurpose {
  PERSONAL_DATA(true),
  CROSS_BORDER_TRANSFER(false),
  MARKETING(false);

  private final boolean mandatory;

  ConsentPurpose(boolean mandatory) {
    this.mandatory = mandatory;
  }

  public boolean isMandatory() {
    return mandatory;
  }
}
