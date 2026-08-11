package com.pravoos.common.web.internal;

import com.pravoos.common.security.internal.InternalCallerHeaders;
import com.pravoos.common.security.internal.InternalCallerVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalSecretFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(InternalSecretFilter.class);
  private static final String INTERNAL_PATH_PREFIX = "/internal/";
  private static final int MAX_LOGGED_CALLER_LENGTH = 64;

  private final InternalCallerVerifier callerVerifier;

  public InternalSecretFilter(InternalCallerVerifier callerVerifier) {
    this.callerVerifier = callerVerifier;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    String caller = request.getHeader(InternalCallerHeaders.CALLER);
    if (!callerVerifier.authorize(
        caller, request.getHeader(InternalCallerHeaders.SECRET), request.getRequestURI())) {
      log.warn(
          "Rejected internal call to {} from caller '{}'",
          request.getRequestURI(),
          sanitize(caller));
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    chain.doFilter(request, response);
  }

  private String sanitize(String caller) {
    if (caller == null || caller.isBlank()) {
      return "<none>";
    }
    if (caller.length() > MAX_LOGGED_CALLER_LENGTH) {
      return "<oversized>";
    }
    return caller.replaceAll("[^A-Za-z0-9._-]", "?");
  }
}
