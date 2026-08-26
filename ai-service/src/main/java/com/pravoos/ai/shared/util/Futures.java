package com.pravoos.ai.shared.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class Futures {

  private Futures() {}

  public static <T> T join(CompletableFuture<T> pending) {
    try {
      return pending.join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw e;
    }
  }
}
