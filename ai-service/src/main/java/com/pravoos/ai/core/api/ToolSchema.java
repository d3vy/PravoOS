package com.pravoos.ai.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

public final class ToolSchema {

  private static final String TYPE_STRING = "string";
  private static final String TYPE_NUMBER = "number";
  private static final String TYPE_BOOLEAN = "boolean";

  private final ObjectMapper objectMapper;
  private final ObjectNode properties;
  private final ArrayNode required;

  private ToolSchema(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.properties = objectMapper.createObjectNode();
    this.required = objectMapper.createArrayNode();
  }

  public static ToolSchema object(ObjectMapper objectMapper) {
    return new ToolSchema(objectMapper);
  }

  public ToolSchema requiredUuid(String field, String description) {
    return add(field, description, TYPE_STRING, true, null, "uuid");
  }

  public ToolSchema optionalUuid(String field, String description) {
    return add(field, description, TYPE_STRING, false, null, "uuid");
  }

  public ToolSchema requiredString(String field, String description) {
    return add(field, description, TYPE_STRING, true, null, null);
  }

  public ToolSchema optionalString(String field, String description) {
    return add(field, description, TYPE_STRING, false, null, null);
  }

  public ToolSchema requiredDate(String field, String description) {
    return add(field, description, TYPE_STRING, true, null, "date");
  }

  public ToolSchema optionalDate(String field, String description) {
    return add(field, description, TYPE_STRING, false, null, "date");
  }

  public ToolSchema requiredNumber(String field, String description) {
    return add(field, description, TYPE_NUMBER, true, null, null);
  }

  public ToolSchema optionalNumber(String field, String description) {
    return add(field, description, TYPE_NUMBER, false, null, null);
  }

  public ToolSchema optionalBoolean(String field, String description) {
    return add(field, description, TYPE_BOOLEAN, false, null, null);
  }

  public ToolSchema requiredEnum(String field, String description, Class<? extends Enum<?>> type) {
    return add(field, description, TYPE_STRING, true, enumValues(type), null);
  }

  public ToolSchema optionalEnum(String field, String description, Class<? extends Enum<?>> type) {
    return add(field, description, TYPE_STRING, false, enumValues(type), null);
  }

  public ToolSchema requiredEnum(String field, String description, Enum<?>... allowedValues) {
    return add(field, description, TYPE_STRING, true, enumValues(allowedValues), null);
  }

  public ToolSchema optionalEnum(String field, String description, Enum<?>... allowedValues) {
    return add(field, description, TYPE_STRING, false, enumValues(allowedValues), null);
  }

  public ObjectNode build() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    schema.set("properties", properties);
    schema.set("required", required);
    schema.put("additionalProperties", false);
    return schema;
  }

  private ToolSchema add(
      String field,
      String description,
      String type,
      boolean mandatory,
      List<String> allowedValues,
      String format) {
    ObjectNode property = objectMapper.createObjectNode();
    property.put("type", type);
    property.put("description", description);
    if (format != null) {
      property.put("format", format);
    }
    if (allowedValues != null) {
      ArrayNode values = objectMapper.createArrayNode();
      allowedValues.forEach(values::add);
      property.set("enum", values);
    }
    properties.set(field, property);
    if (mandatory) {
      required.add(field);
    }
    return this;
  }

  private static List<String> enumValues(Class<? extends Enum<?>> type) {
    return enumValues(type.getEnumConstants());
  }

  private static List<String> enumValues(Enum<?>[] constants) {
    if (constants.length == 0) {
      throw new IllegalArgumentException("Enum schema needs at least one allowed value");
    }
    return java.util.Arrays.stream(constants).map(Enum::name).toList();
  }
}
