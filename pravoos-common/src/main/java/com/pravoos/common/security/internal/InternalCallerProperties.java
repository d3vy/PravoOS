package com.pravoos.common.security.internal;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal")
public record InternalCallerProperties(Map<String, Caller> callers) {

  public record Caller(String secret, List<String> paths) {

    public Caller {
      paths = paths == null ? List.of() : List.copyOf(paths);
    }

    public boolean allows(String requestPath) {
      return paths.stream().anyMatch(requestPath::startsWith);
    }

    public boolean configured() {
      return secret != null && !secret.isBlank();
    }
  }

  public InternalCallerProperties {
    callers = callers == null ? Map.of() : Map.copyOf(callers);
  }

  public Caller caller(String name) {
    return name == null ? null : callers.get(name);
  }
}
