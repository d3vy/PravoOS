package com.pravoos.common.security.internal;

import com.pravoos.common.security.internal.InternalCallerProperties.Caller;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class InternalCallerVerifier {

  private final InternalCallerProperties callerProperties;

  public InternalCallerVerifier(InternalCallerProperties callerProperties) {
    this.callerProperties = callerProperties;
  }

  public boolean authorize(String callerName, String presentedSecret, String requestPath) {
    Caller caller = callerProperties.caller(callerName);
    if (caller == null || !caller.configured()) {
      return false;
    }
    if (!secretMatches(caller.secret(), presentedSecret)) {
      return false;
    }
    return caller.allows(requestPath);
  }

  private boolean secretMatches(String configuredSecret, String presentedSecret) {
    return presentedSecret != null
        && MessageDigest.isEqual(sha256(configuredSecret), sha256(presentedSecret));
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
