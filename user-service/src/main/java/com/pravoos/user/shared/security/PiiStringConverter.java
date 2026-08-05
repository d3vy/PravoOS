package com.pravoos.user.shared.security;

import com.pravoos.common.security.PiiCryptoHolder;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PiiStringConverter implements AttributeConverter<String, String> {

  @Override
  public String convertToDatabaseColumn(String attribute) {
    return PiiCryptoHolder.encryptor().encrypt(attribute);
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    return PiiCryptoHolder.encryptor().decrypt(dbData);
  }
}
