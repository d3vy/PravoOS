package com.pravoos.llm.pii;

import java.util.function.Consumer;

public class StreamRestorer implements Consumer<String> {

  private static final int MAX_PLACEHOLDER_LENGTH = 32;

  private final RedactionSession session;
  private final Consumer<String> downstream;
  private final StringBuilder pending = new StringBuilder();

  public StreamRestorer(RedactionSession session, Consumer<String> downstream) {
    this.session = session;
    this.downstream = downstream;
  }

  @Override
  public void accept(String token) {
    if (session.isEmpty()) {
      downstream.accept(token);
      return;
    }
    pending.append(token);
    int openBracket = lastUnclosedBracket();
    String emittable = openBracket < 0 ? pending.toString() : pending.substring(0, openBracket);
    String held = openBracket < 0 ? "" : pending.substring(openBracket);
    pending.setLength(0);
    pending.append(held);
    if (!emittable.isEmpty()) {
      downstream.accept(session.restore(emittable));
    }
  }

  public void flush() {
    if (pending.length() > 0) {
      downstream.accept(session.restore(pending.toString()));
      pending.setLength(0);
    }
  }

  private int lastUnclosedBracket() {
    int openBracket = pending.lastIndexOf("[");
    if (openBracket < 0) {
      return -1;
    }
    if (pending.indexOf("]", openBracket) >= 0) {
      return -1;
    }
    return pending.length() - openBracket > MAX_PLACEHOLDER_LENGTH ? -1 : openBracket;
  }
}
