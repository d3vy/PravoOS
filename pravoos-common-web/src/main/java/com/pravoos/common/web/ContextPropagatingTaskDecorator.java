package com.pravoos.common.web;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class ContextPropagatingTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable task) {
    SecurityContext securityContext = SecurityContextHolder.getContext();
    Map<String, String> loggingContext = MDC.getCopyOfContextMap();
    return () -> {
      SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
      Map<String, String> previousLoggingContext = MDC.getCopyOfContextMap();
      SecurityContextHolder.setContext(securityContext);
      applyLoggingContext(loggingContext);
      try {
        task.run();
      } finally {
        SecurityContextHolder.setContext(previousSecurityContext);
        applyLoggingContext(previousLoggingContext);
      }
    };
  }

  private void applyLoggingContext(Map<String, String> loggingContext) {
    if (loggingContext == null) {
      MDC.clear();
      return;
    }
    MDC.setContextMap(loggingContext);
  }
}
