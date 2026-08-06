package com.pravoos.ai.court.api;

import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CourtCaseNumberParser {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private static final Pattern ARBITR_NUMBER =
      Pattern.compile("^[АA]\\d{1,3}-\\d{1,9}/\\d{4}.*", Pattern.CASE_INSENSITIVE);

  private static final Pattern GENERAL_JURISDICTION_NUMBER =
      Pattern.compile("^\\d{1,3}[А-Яа-яA-Za-z]?-\\d{1,9}/\\d{4}.*");

  private static final Pattern NUMBER_IN_TEXT =
      Pattern.compile(
          "(?<![\\p{L}\\p{N}])([АA]?\\d{1,3}[А-Яа-яA-Za-z]?-\\d{1,9}/\\d{4})(?!\\p{N})",
          Pattern.UNICODE_CASE);

  private static final char LATIN_A = 'A';
  private static final char CYRILLIC_A = 'А';

  private CourtCaseNumberParser() {}

  public static List<String> extractAll(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    Matcher matcher = NUMBER_IN_TEXT.matcher(text);
    List<String> numbers = new ArrayList<>();
    while (matcher.find()) {
      String candidate = normalize(matcher.group(1));
      if (candidate != null && detect(candidate).isPresent() && !numbers.contains(candidate)) {
        numbers.add(candidate);
      }
    }
    return List.copyOf(numbers);
  }

  public static String canonical(String caseNumber) {
    String normalized = normalize(caseNumber);
    if (normalized == null) {
      return null;
    }
    return normalized.toUpperCase(Locale.ROOT).replace(LATIN_A, CYRILLIC_A);
  }

  public static String normalize(String caseNumber) {
    if (caseNumber == null) {
      return null;
    }
    String normalized = WHITESPACE.matcher(caseNumber.trim()).replaceAll("");
    return normalized.isEmpty() ? null : normalized;
  }

  public static Optional<CourtSystem> detect(String caseNumber) {
    String normalized = normalize(caseNumber);
    if (normalized == null) {
      return Optional.empty();
    }
    if (ARBITR_NUMBER.matcher(normalized).matches()) {
      return Optional.of(CourtSystem.ARBITR);
    }
    if (GENERAL_JURISDICTION_NUMBER.matcher(normalized).matches()) {
      return Optional.of(CourtSystem.GENERAL_JURISDICTION);
    }
    return Optional.empty();
  }

  public static CourtSystem detectOrDefault(String caseNumber, CourtSystem fallback) {
    return detect(caseNumber).orElse(fallback);
  }
}
