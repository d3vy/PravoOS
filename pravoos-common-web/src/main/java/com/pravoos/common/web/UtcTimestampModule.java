package com.pravoos.common.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public final class UtcTimestampModule extends SimpleModule {

  public UtcTimestampModule() {
    super("pravoos-utc-timestamps");
    addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
    addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
  }

  private static final class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider)
        throws IOException {
      generator.writeString(value.toInstant(ZoneOffset.UTC).toString());
    }
  }

  private static final class UtcLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      String text = parser.getText();
      if (text == null || text.isBlank()) {
        return null;
      }
      String trimmed = text.trim();
      try {
        return OffsetDateTime.parse(trimmed)
            .withOffsetSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
      } catch (DateTimeParseException withoutOffset) {
        return LocalDateTime.parse(trimmed);
      }
    }
  }
}
