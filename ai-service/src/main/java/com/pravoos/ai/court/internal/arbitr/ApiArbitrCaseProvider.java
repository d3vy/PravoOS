package com.pravoos.ai.court.internal.arbitr;

import com.pravoos.ai.court.api.CourtCaseData;
import com.pravoos.ai.court.internal.CourtCaseProvider;
import com.pravoos.ai.court.internal.arbitr.dto.ArbitrApiResponse;
import com.pravoos.ai.court.internal.config.ArbitrProperties;
import com.pravoos.ai.shared.exception.CourtIntegrationException;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ApiArbitrCaseProvider implements CourtCaseProvider {

  private static final Logger log = LoggerFactory.getLogger(ApiArbitrCaseProvider.class);

  private final RestClient restClient;
  private final ArbitrProperties properties;

  public ApiArbitrCaseProvider(RestClient arbitrRestClient, ArbitrProperties properties) {
    this.restClient = arbitrRestClient;
    this.properties = properties;
  }

  @Override
  public CourtSystem system() {
    return CourtSystem.ARBITR;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public Optional<CourtCaseData> fetchCase(String caseNumber) {
    if (caseNumber == null || caseNumber.isBlank()) {
      return Optional.empty();
    }
    ArbitrApiResponse response = requestDetails(caseNumber.trim());
    if (response == null) {
      return Optional.empty();
    }
    if (response.success() == null || response.success() != 1) {
      log.info("КАД.Арбитр: Success != 1 по делу {} (error={})", caseNumber, response.error());
      return Optional.empty();
    }
    if (response.cases() == null || response.cases().isEmpty()) {
      log.info("КАД.Арбитр: дело {} не найдено", caseNumber);
      return Optional.empty();
    }
    return Optional.of(mapToCaseData(response.cases().get(0)));
  }

  private ArbitrApiResponse requestDetails(String caseNumber) {
    try {
      return restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .queryParam("key", properties.api().key())
                      .queryParam("CaseNumber", caseNumber)
                      .build())
          .retrieve()
          .body(ArbitrApiResponse.class);
    } catch (RestClientResponseException e) {
      log.error("КАД.Арбитр API вернул {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
      throw new CourtIntegrationException(
          "КАД.Арбитр API вернул статус " + e.getStatusCode().value());
    } catch (RestClientException e) {
      log.error("Ошибка вызова КАД.Арбитр API по делу {}: {}", caseNumber, e.getMessage());
      throw new CourtIntegrationException("Ошибка вызова КАД.Арбитр API: " + e.getMessage(), e);
    }
  }

  private CourtCaseData mapToCaseData(ArbitrApiResponse.Case caseDto) {
    List<CourtCaseData.CourtEvent> events = new ArrayList<>();
    for (ArbitrApiResponse.CaseInstance instance : safe(caseDto.caseInstances())) {
      String courtName = instance.court() != null ? instance.court().name() : null;
      for (ArbitrApiResponse.InstanceEvent event : safe(instance.instanceEvents())) {
        events.add(toEvent(event, courtName));
      }
    }
    return new CourtCaseData(
        caseDto.caseNumber(),
        caseDto.caseId(),
        resolveNextHearingDate(caseDto),
        resolveJudgeName(caseDto),
        resolveParties(caseDto),
        events);
  }

  private String resolveJudgeName(ArbitrApiResponse.Case caseDto) {
    String fromHearing =
        safe(caseDto.courtHearings()).stream()
            .map(ArbitrApiResponse.CourtHearing::judge)
            .map(this::judgeName)
            .filter(name -> name != null && !name.isBlank())
            .findFirst()
            .orElse(null);
    if (fromHearing != null) {
      return fromHearing.trim();
    }
    return safe(caseDto.caseInstances()).stream()
        .map(ArbitrApiResponse.CaseInstance::judge)
        .map(this::judgeName)
        .filter(name -> name != null && !name.isBlank())
        .map(String::trim)
        .findFirst()
        .orElse(null);
  }

  private String judgeName(ArbitrApiResponse.Judge judge) {
    return judge == null ? null : judge.name();
  }

  private List<CourtCaseData.CourtParty> resolveParties(ArbitrApiResponse.Case caseDto) {
    List<CourtCaseData.CourtParty> parties = new ArrayList<>();
    for (ArbitrApiResponse.Side side : safe(caseDto.sides())) {
      if (side.name() == null || side.name().isBlank()) {
        continue;
      }
      String role = side.type() == null || side.type().isBlank() ? null : side.type().trim();
      parties.add(new CourtCaseData.CourtParty(side.name().trim(), role));
    }
    return parties;
  }

  private CourtCaseData.CourtEvent toEvent(
      ArbitrApiResponse.InstanceEvent event, String courtName) {
    LocalDate date = parseIsoDate(event.date());
    String description =
        Stream.of(event.eventContentTypeName(), event.additionalInfo())
            .filter(value -> value != null && !value.isBlank())
            .reduce((first, second) -> first + " — " + second)
            .orElse(null);
    String sourceEventId =
        event.id() != null && !event.id().isBlank()
            ? event.id()
            : fallbackSourceEventId(courtName, event.date(), event.eventTypeName());
    return new CourtCaseData.CourtEvent(
        sourceEventId, date, event.eventTypeName(), description, courtName);
  }

  private LocalDate resolveNextHearingDate(ArbitrApiResponse.Case caseDto) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate nextHearingDate = null;
    for (ArbitrApiResponse.CourtHearing hearing : safe(caseDto.courtHearings())) {
      LocalDate hearingDate = parseHearingDate(hearing.start());
      if (hearingDate != null
          && !hearingDate.isBefore(today)
          && (nextHearingDate == null || hearingDate.isBefore(nextHearingDate))) {
        nextHearingDate = hearingDate;
      }
    }
    return nextHearingDate;
  }

  private LocalDate parseIsoDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      log.debug("КАД.Арбитр: не удалось разобрать дату '{}'", value);
      return null;
    }
  }

  private LocalDate parseHearingDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return parseIsoDate(value.trim().split("T")[0]);
  }

  private String fallbackSourceEventId(String courtName, String date, String eventType) {
    String key =
        String.join(
            "|",
            courtName == null ? "" : courtName,
            date == null ? "" : date,
            eventType == null ? "" : eventType);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new CourtIntegrationException("SHA-256 недоступен в среде выполнения", e);
    }
  }

  private <T> List<T> safe(List<T> list) {
    return list == null ? List.of() : list;
  }
}
