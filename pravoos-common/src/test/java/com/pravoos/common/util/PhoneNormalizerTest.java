package com.pravoos.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pravoos.common.exception.InvalidPhoneNumberException;
import org.junit.jupiter.api.Test;

class PhoneNormalizerTest {

  @Test
  void normalizesRussianEightPrefixToPlusSeven() {
    assertThat(PhoneNormalizer.normalize("8 (912) 345-67-89")).isEqualTo("+79123456789");
  }

  @Test
  void keepsSevenPrefixAndStripsFormatting() {
    assertThat(PhoneNormalizer.normalize("+7 912 345 67 89")).isEqualTo("+79123456789");
  }

  @Test
  void nullPhoneIsRejected() {
    assertThatThrownBy(() -> PhoneNormalizer.normalize(null))
        .isInstanceOf(InvalidPhoneNumberException.class);
  }

  @Test
  void wrongLengthIsRejected() {
    assertThatThrownBy(() -> PhoneNormalizer.normalize("12345"))
        .isInstanceOf(InvalidPhoneNumberException.class);
  }

  @Test
  void wrongCountryCodeIsRejected() {
    assertThatThrownBy(() -> PhoneNormalizer.normalize("+1 202 555 0100"))
        .isInstanceOf(InvalidPhoneNumberException.class);
  }
}
