package com.pravoos.llm.pii;

import java.util.regex.Pattern;

public enum PiiPattern {
  EMAIL("EMAIL", Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]{2,}")),
  PHONE(
      "PHONE",
      Pattern.compile(
          "(?<![\\d-])(?:\\+7|8)[\\s(-]{0,2}\\d{3}[\\s)-]{0,2}\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}(?![\\d-])")),
  CARD("CARD", Pattern.compile("(?<!\\d)\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}(?!\\d)")),
  SNILS(
      "SNILS", Pattern.compile("(?<!\\d)(?:\\d{3}[ -]\\d{3}[ -]\\d{3}[ -]\\d{2}|\\d{11})(?!\\d)")),
  PASSPORT(
      "PASSPORT",
      Pattern.compile("(?<!\\d)(?:\\d{2}\\s\\d{2}|\\d{4})(?:\\s+|\\s*№\\s*)\\d{6}(?!\\d)")),
  INN("INN", Pattern.compile("(?<!\\d)(?:\\d{12}|\\d{10})(?!\\d)")),
  FULL_NAME(
      "NAME",
      Pattern.compile(
          "\\b[А-ЯЁ][а-яё]+(?:ов|ев|ёв|ин|ын|ский|цкий|ская|цкая|ова|ева|ёва|ина|ына|ко|ук|юк|ян|дзе|швили)"
              + "\\s+[А-ЯЁ][а-яё]+\\s+[А-ЯЁ][а-яё]+(?:ович|евич|ьич|овна|евна|ична|инична)\\b",
          Pattern.UNICODE_CHARACTER_CLASS)),
  NAME_WITH_INITIALS(
      "NAME",
      Pattern.compile(
          "\\b[А-ЯЁ][а-яё]+(?:ов|ев|ёв|ин|ын|ский|цкий|ская|цкая|ова|ева|ёва|ина|ына|ко|ук|юк|ян|дзе|швили)"
              + "\\s+[А-ЯЁ]\\.\\s?[А-ЯЁ]\\.",
          Pattern.UNICODE_CHARACTER_CLASS)),
  PATRONYMIC_NAME(
      "NAME",
      Pattern.compile(
          "\\b[А-ЯЁ][а-яё]+\\s+[А-ЯЁ][а-яё]+(?:ович|евич|ьич|овна|евна|ична|инична)\\b",
          Pattern.UNICODE_CHARACTER_CLASS));

  private final String label;
  private final Pattern pattern;

  PiiPattern(String label, Pattern pattern) {
    this.label = label;
    this.pattern = pattern;
  }

  public String label() {
    return label;
  }

  public Pattern pattern() {
    return pattern;
  }
}
