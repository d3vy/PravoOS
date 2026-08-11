package com.pravoos.common.security.internal;

import com.pravoos.common.security.internal.InternalCallerProperties.Caller;
import java.util.List;
import java.util.Map;

public final class InternalCallerCheck {

  private InternalCallerCheck() {}

  public static void requireEveryCallerConfigured(InternalCallerProperties callerProperties) {
    if (callerProperties.callers().isEmpty()) {
      throw new IllegalStateException(
          "No internal callers configured. /internal/** would reject every service-to-service "
              + "call, breaking portal invites, notifications and LLM access.");
    }
    List<String> misconfigured =
        callerProperties.callers().entrySet().stream()
            .filter(entry -> !configured(entry.getValue()))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    if (!misconfigured.isEmpty()) {
      throw new IllegalStateException(
          "Missing per-caller internal secrets in production (profile 'docker') for: "
              + String.join(", ", misconfigured)
              + ". Set a distinct INTERNAL_SECRET_* value for each calling service.");
    }
  }

  private static boolean configured(Caller caller) {
    return caller != null && caller.configured() && !caller.paths().isEmpty();
  }
}
