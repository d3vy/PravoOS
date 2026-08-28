package com.pravoos.ai.core.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public final class ToolArguments {

  private ToolArguments() {}

  public static UUID requireUuid(JsonNode arguments, String field) {
    return parseUuid(requireText(arguments, field), field);
  }

  public static UUID optionalUuid(JsonNode arguments, String field) {
    String value = optionalText(arguments, field);
    return value == null ? null : parseUuid(value, field);
  }

  public static String requireText(JsonNode arguments, String field) {
    String value = optionalText(arguments, field);
    if (value == null) {
      throw missing(field);
    }
    return value;
  }

  public static String optionalText(JsonNode arguments, String field) {
    JsonNode value = arguments == null ? null : arguments.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      return null;
    }
    return value.asText().strip();
  }

  public static LocalDate requireDate(JsonNode arguments, String field) {
    return parseDate(requireText(arguments, field), field);
  }

  public static LocalDate optionalDate(JsonNode arguments, String field) {
    String value = optionalText(arguments, field);
    return value == null ? null : parseDate(value, field);
  }

  public static BigDecimal requireDecimal(JsonNode arguments, String field) {
    BigDecimal value = optionalDecimal(arguments, field);
    if (value == null) {
      throw missing(field);
    }
    return value;
  }

  public static BigDecimal optionalDecimal(JsonNode arguments, String field) {
    JsonNode value = arguments == null ? null : arguments.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isNumber()) {
      return value.decimalValue();
    }
    if (value.isTextual() && !value.asText().isBlank()) {
      try {
        return new BigDecimal(value.asText().strip());
      } catch (NumberFormatException ex) {
        throw invalid(field, "числом");
      }
    }
    throw invalid(field, "числом");
  }

  public static <E extends Enum<E>> E requireEnum(JsonNode arguments, String field, Class<E> type) {
    return parseEnum(requireText(arguments, field), field, type);
  }

  public static <E extends Enum<E>> E optionalEnum(
      JsonNode arguments, String field, Class<E> type) {
    String value = optionalText(arguments, field);
    return value == null ? null : parseEnum(value, field, type);
  }

  private static UUID parseUuid(String value, String field) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw invalid(field, "идентификатором в формате UUID");
    }
  }

  private static LocalDate parseDate(String value, String field) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException ex) {
      throw invalid(field, "датой в формате ГГГГ-ММ-ДД");
    }
  }

  private static <E extends Enum<E>> E parseEnum(String value, String field, Class<E> type) {
    for (E candidate : type.getEnumConstants()) {
      if (candidate.name().equalsIgnoreCase(value)) {
        return candidate;
      }
    }
    throw new InvalidToolArgumentException(
        "Аргумент '"
            + field
            + "' должен быть одним из значений: "
            + String.join(", ", enumNames(type))
            + ".");
  }

  private static <E extends Enum<E>> String[] enumNames(Class<E> type) {
    E[] constants = type.getEnumConstants();
    String[] names = new String[constants.length];
    for (int i = 0; i < constants.length; i++) {
      names[i] = constants[i].name();
    }
    return names;
  }

  private static InvalidToolArgumentException missing(String field) {
    return new InvalidToolArgumentException("Обязательный аргумент '" + field + "' не заполнен.");
  }

  private static InvalidToolArgumentException invalid(String field, String expectation) {
    return new InvalidToolArgumentException(
        "Аргумент '" + field + "' должен быть " + expectation + ".");
  }
}
