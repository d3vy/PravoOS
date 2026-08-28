package com.pravoos.ai.recyclebin.api;

public enum RecycleBinEntityType {
  CASE(RecycleBinArea.CASES),
  CLIENT(RecycleBinArea.CLIENTS),
  DOCUMENT(RecycleBinArea.DOCUMENTS),
  INVOICE(RecycleBinArea.INVOICES),
  CONVERSATION(RecycleBinArea.CHAT);

  private final RecycleBinArea area;

  RecycleBinEntityType(RecycleBinArea area) {
    this.area = area;
  }

  public RecycleBinArea area() {
    return area;
  }
}
