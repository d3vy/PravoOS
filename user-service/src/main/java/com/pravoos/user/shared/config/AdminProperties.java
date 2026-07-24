package com.pravoos.user.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(List<Account> accounts) {
  public record Account(String email, String password) {}
}
