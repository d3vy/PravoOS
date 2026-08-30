package com.pravoos.ai.recyclebin.api;

public enum RecycleBinEntityType {
  CASE(RecycleBinArea.CASES),
  CLIENT(RecycleBinArea.CLIENTS),
  DOCUMENT(RecycleBinArea.DOCUMENTS),
  INVOICE(RecycleBinArea.INVOICES),
  CONVERSATION(RecycleBinArea.CHAT),
  CASE_TASK(RecycleBinArea.CASES),
  CLIENT_CONTACT(RecycleBinArea.CLIENTS),
  TEMPLATE(RecycleBinArea.TEMPLATES),
  WORKFLOW_DEFINITION(RecycleBinArea.WORKFLOWS),
  MAILBOX(RecycleBinArea.MAILBOXES),
  SAVED_VIEW(RecycleBinArea.VIEWS),
  TABULAR_REVIEW(RecycleBinArea.REVIEW),
  TIME_ENTRY(RecycleBinArea.TIME);

  private final RecycleBinArea area;

  RecycleBinEntityType(RecycleBinArea area) {
    this.area = area;
  }

  public RecycleBinArea area() {
    return area;
  }
}
