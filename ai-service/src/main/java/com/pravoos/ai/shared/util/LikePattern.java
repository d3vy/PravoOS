package com.pravoos.ai.shared.util;

public final class LikePattern {

  private LikePattern() {}

  public static String contains(String query) {
    String escaped = query.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }
}
