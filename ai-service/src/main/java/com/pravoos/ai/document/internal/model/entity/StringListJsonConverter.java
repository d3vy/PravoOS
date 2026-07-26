package com.pravoos.ai.document.internal.model.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

  private static final Logger log = LoggerFactory.getLogger(StringListJsonConverter.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<String> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(attribute);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to serialize string list", e);
    }
  }

  @Override
  public List<String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(dbData, LIST_TYPE);
    } catch (Exception e) {
      log.warn("Failed to deserialize string list column, treating as empty: {}", e.getMessage());
      return List.of();
    }
  }
}
