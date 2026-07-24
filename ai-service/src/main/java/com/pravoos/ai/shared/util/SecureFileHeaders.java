package com.pravoos.ai.shared.util;

import org.springframework.http.HttpHeaders;

public final class SecureFileHeaders {

  private SecureFileHeaders() {}

  public static void apply(HttpHeaders headers) {
    headers.set("X-Content-Type-Options", "nosniff");
    headers.set("Content-Security-Policy", "sandbox");
  }
}
