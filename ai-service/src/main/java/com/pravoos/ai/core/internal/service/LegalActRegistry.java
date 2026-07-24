package com.pravoos.ai.core.internal.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LegalActRegistry {

  private static final int FLAGS =
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

  public record ActMatch(String canonicalName, String matchedText) {}

  private record Entry(Pattern pattern, String canonicalName) {}

  private final List<Entry> entries =
      List.of(
          entry(
              "(?:гражданск\\w+\\s+процессуальн\\w+\\s+кодекс\\w*|ГПК(?:\\s*РФ)?)",
              "Гражданский процессуальный кодекс РФ"),
          entry(
              "(?:арбитражн\\w+\\s+процессуальн\\w+\\s+кодекс\\w*|АПК(?:\\s*РФ)?)",
              "Арбитражный процессуальный кодекс РФ"),
          entry(
              "(?:кодекс\\w*\\s+административн\\w+\\s+судопроизводств\\w*|КАС(?:\\s*РФ)?)",
              "Кодекс административного судопроизводства РФ"),
          entry(
              "(?:уголовно-процессуальн\\w+\\s+кодекс\\w*|УПК(?:\\s*РФ)?)",
              "Уголовно-процессуальный кодекс РФ"),
          entry(
              "(?:кодекс\\w*\\s+об\\s+административн\\w+\\s+правонарушени\\w*|КоАП(?:\\s*РФ)?)",
              "Кодекс РФ об административных правонарушениях"),
          entry(
              "(?:гражданск\\w+\\s+кодекс\\w*|ГК\\s*РФ|(?<![А-ЯЁа-яё])ГК(?![А-ЯЁа-яё]))",
              "Гражданский кодекс РФ"),
          entry("(?:уголовн\\w+\\s+кодекс\\w*|УК\\s*РФ)", "Уголовный кодекс РФ"),
          entry("(?:налогов\\w+\\s+кодекс\\w*|НК\\s*РФ)", "Налоговый кодекс РФ"),
          entry("(?:трудов\\w+\\s+кодекс\\w*|ТК\\s*РФ)", "Трудовой кодекс РФ"),
          entry("(?:семейн\\w+\\s+кодекс\\w*|СК\\s*РФ)", "Семейный кодекс РФ"),
          entry("(?:жилищн\\w+\\s+кодекс\\w*|ЖК\\s*РФ)", "Жилищный кодекс РФ"),
          entry("(?:земельн\\w+\\s+кодекс\\w*|ЗК\\s*РФ)", "Земельный кодекс РФ"),
          entry("(?:бюджетн\\w+\\s+кодекс\\w*|БК\\s*РФ)", "Бюджетный кодекс РФ"),
          entry(
              "(?:закон\\w*\\s+о\\s+(?:несостоятельности|банкротстве)"
                  + "|о\\s+несостоятельности\\s*\\(банкротстве\\)"
                  + "|(?:№\\s*|N\\s*)?127-ФЗ)",
              "Федеральный закон № 127-ФЗ «О несостоятельности (банкротстве)»"));

  public Optional<ActMatch> recognize(String window) {
    if (window == null || window.isBlank()) {
      return Optional.empty();
    }
    for (Entry entry : entries) {
      Matcher matcher = entry.pattern().matcher(window);
      if (matcher.find()) {
        return Optional.of(new ActMatch(entry.canonicalName(), matcher.group().strip()));
      }
    }
    return Optional.empty();
  }

  private static Entry entry(String regex, String canonicalName) {
    return new Entry(Pattern.compile(regex, FLAGS), canonicalName);
  }
}
