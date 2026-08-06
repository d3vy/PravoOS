package com.pravoos.ai.court.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.shared.model.enums.CourtSystem;
import org.junit.jupiter.api.Test;

class CourtCaseNumberParserTest {

  @Test
  void detectsArbitrNumber() {
    assertThat(CourtCaseNumberParser.detect("А40-123456/2024")).contains(CourtSystem.ARBITR);
    assertThat(CourtCaseNumberParser.detect("A40-123456/2024")).contains(CourtSystem.ARBITR);
  }

  @Test
  void detectsGeneralJurisdictionNumber() {
    assertThat(CourtCaseNumberParser.detect("2-1234/2024"))
        .contains(CourtSystem.GENERAL_JURISDICTION);
    assertThat(CourtCaseNumberParser.detect("2а-15/2023"))
        .contains(CourtSystem.GENERAL_JURISDICTION);
  }

  @Test
  void ignoresWhitespaceAroundAndInsideNumber() {
    assertThat(CourtCaseNumberParser.normalize("  А40-1/2026 ")).isEqualTo("А40-1/2026");
    assertThat(CourtCaseNumberParser.detect(" А40 -1/2026 ")).contains(CourtSystem.ARBITR);
  }

  @Test
  void returnsEmptyForBlankAndUnknownFormats() {
    assertThat(CourtCaseNumberParser.normalize("   ")).isNull();
    assertThat(CourtCaseNumberParser.normalize(null)).isNull();
    assertThat(CourtCaseNumberParser.detect("дело о банкротстве")).isEmpty();
    assertThat(CourtCaseNumberParser.detect("А40/2024")).isEmpty();
  }

  @Test
  void extractsCaseNumbersFromFreeText() {
    assertThat(CourtCaseNumberParser.extractAll("Re: дело № А40-123456/2024, заседание"))
        .containsExactly("А40-123456/2024");
    assertThat(CourtCaseNumberParser.extractAll("Определения по 2а-15/2023 и A40-1/2026"))
        .containsExactly("2а-15/2023", "A40-1/2026");
    assertThat(CourtCaseNumberParser.extractAll("Счёт 12345/2024 оплачен")).isEmpty();
    assertThat(CourtCaseNumberParser.extractAll(null)).isEmpty();
  }

  @Test
  void canonicalFormMatchesLatinAndCyrillicSpelling() {
    assertThat(CourtCaseNumberParser.canonical("a40-1/2026"))
        .isEqualTo(CourtCaseNumberParser.canonical("А40-1/2026"));
    assertThat(CourtCaseNumberParser.canonical("  ")).isNull();
  }

  @Test
  void fallsBackToProvidedSystemWhenFormatUnknown() {
    assertThat(CourtCaseNumberParser.detectOrDefault(null, CourtSystem.ARBITR))
        .isEqualTo(CourtSystem.ARBITR);
    assertThat(
            CourtCaseNumberParser.detectOrDefault("без номера", CourtSystem.GENERAL_JURISDICTION))
        .isEqualTo(CourtSystem.GENERAL_JURISDICTION);
  }
}
