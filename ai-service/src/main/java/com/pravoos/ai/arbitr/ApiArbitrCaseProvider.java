package com.pravoos.ai.arbitr;

import com.pravoos.ai.arbitr.dto.ArbitrApiResponse;
import com.pravoos.ai.config.ArbitrProperties;
import com.pravoos.ai.exception.ArbitrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public class ApiArbitrCaseProvider implements ArbitrCaseProvider {

    private static final Logger log = LoggerFactory.getLogger(ApiArbitrCaseProvider.class);
    private static final DateTimeFormatter EVENT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RestClient restClient;
    private final ArbitrProperties properties;

    public ApiArbitrCaseProvider(RestClient arbitrRestClient, ArbitrProperties properties) {
        this.restClient = arbitrRestClient;
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Optional<ArbitrCaseData> fetchCase(String arbitrCaseNumber) {
        if (arbitrCaseNumber == null || arbitrCaseNumber.isBlank()) {
            return Optional.empty();
        }
        ArbitrApiResponse response = requestCaseInfo(arbitrCaseNumber.trim());
        if (response == null || response.result() == null) {
            log.info("КАД.Арбитр: пустой ответ по делу {}", arbitrCaseNumber);
            return Optional.empty();
        }
        if (Boolean.FALSE.equals(response.found())) {
            log.info("КАД.Арбитр: дело {} не найдено", arbitrCaseNumber);
            return Optional.empty();
        }
        return Optional.of(mapToCaseData(arbitrCaseNumber, response.result()));
    }

    private ArbitrApiResponse requestCaseInfo(String caseNumber) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("type", "caseInfo")
                            .queryParam("CaseNumber", caseNumber)
                            .queryParam("token", properties.api().token())
                            .build())
                    .retrieve()
                    .body(ArbitrApiResponse.class);
        } catch (RestClientResponseException e) {
            log.error("КАД.Арбитр API вернул {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ArbitrException("КАД.Арбитр API вернул статус " + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.error("Ошибка вызова КАД.Арбитр API по делу {}: {}", caseNumber, e.getMessage());
            throw new ArbitrException("Ошибка вызова КАД.Арбитр API: " + e.getMessage(), e);
        }
    }

    private ArbitrCaseData mapToCaseData(String requestedNumber, ArbitrApiResponse.Result result) {
        String caseGuid = result.caseInfo() != null ? result.caseInfo().caseId() : null;
        String resolvedNumber = result.caseInfo() != null && result.caseInfo().caseNumber() != null
                ? result.caseInfo().caseNumber()
                : requestedNumber;

        List<ArbitrCaseData.ArbitrEvent> events = new ArrayList<>();
        LocalDate nextHearingDate = null;
        LocalDate today = LocalDate.now();

        for (ArbitrApiResponse.CaseInstance instance : safe(result.caseInstances())) {
            String courtName = instance.court() != null ? instance.court().name() : null;
            for (ArbitrApiResponse.InstanceEvent event : safe(instance.instanceEvents())) {
                events.add(toEvent(event, courtName));
            }
            for (ArbitrApiResponse.CourtHearing hearing : safe(instance.courtHearings())) {
                LocalDate hearingDate = parseHearingDate(hearing.start());
                if (hearingDate != null && !hearingDate.isBefore(today)
                        && (nextHearingDate == null || hearingDate.isBefore(nextHearingDate))) {
                    nextHearingDate = hearingDate;
                }
            }
        }

        return new ArbitrCaseData(resolvedNumber, caseGuid, nextHearingDate, events);
    }

    private ArbitrCaseData.ArbitrEvent toEvent(ArbitrApiResponse.InstanceEvent event, String courtName) {
        LocalDate date = parseEventDate(event.date());
        String description = event.contentTypes() == null || event.contentTypes().isEmpty()
                ? null
                : String.join("; ", event.contentTypes());
        String sourceEventId = buildSourceEventId(courtName, event.date(), event.publishDate(), event.eventTypeName());
        return new ArbitrCaseData.ArbitrEvent(sourceEventId, date, event.eventTypeName(), description, courtName);
    }

    private String buildSourceEventId(String courtName, String date, String publishDate, String eventType) {
        String key = String.join("|",
                courtName == null ? "" : courtName,
                date == null ? "" : date,
                publishDate == null ? "" : publishDate,
                eventType == null ? "" : eventType);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new ArbitrException("SHA-256 недоступен в среде выполнения", e);
        }
    }

    private LocalDate parseEventDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), EVENT_DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseHearingDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String datePart = value.trim().split("T")[0];
        try {
            return LocalDate.parse(datePart);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
