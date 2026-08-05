package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.dto.CitationCheck;
import com.pravoos.ai.core.internal.dto.CitationCheckResult;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.core.internal.service.CitationExtractor.ExtractedCitation;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.document.api.LegislationRef;
import com.pravoos.ai.shared.config.CitationCheckProperties;
import com.pravoos.ai.shared.court.CourtCaseNumberParser;
import com.pravoos.ai.shared.court.CourtCaseProvider;
import com.pravoos.ai.shared.court.CourtCaseProviderRegistry;
import com.pravoos.ai.shared.exception.AiResponseNotFoundException;
import com.pravoos.ai.shared.model.enums.CitationStatus;
import com.pravoos.ai.shared.model.enums.CourtSystem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CitationCheckService {

  private static final Logger log = LoggerFactory.getLogger(CitationCheckService.class);
  private static final DateTimeFormatter EDITION_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final CitationExtractor citationExtractor;
  private final CourtCaseProviderRegistry courtCaseProviderRegistry;
  private final DocumentAccess documentAccess;
  private final AiResponseRepository aiResponseRepository;
  private final CitationCheckProperties properties;
  private final Map<CitationStatus, Counter> statusCounters;

  public CitationCheckService(
      CitationExtractor citationExtractor,
      CourtCaseProviderRegistry courtCaseProviderRegistry,
      DocumentAccess documentAccess,
      AiResponseRepository aiResponseRepository,
      CitationCheckProperties properties,
      MeterRegistry registry) {
    this.citationExtractor = citationExtractor;
    this.courtCaseProviderRegistry = courtCaseProviderRegistry;
    this.documentAccess = documentAccess;
    this.aiResponseRepository = aiResponseRepository;
    this.properties = properties;
    this.statusCounters = new EnumMap<>(CitationStatus.class);
    for (CitationStatus status : CitationStatus.values()) {
      statusCounters.put(
          status,
          Counter.builder("pravoos.citation.checks")
              .description("Citation verification outcomes")
              .tag("status", status.name().toLowerCase())
              .register(registry));
    }
  }

  @Transactional(readOnly = true)
  public CitationCheckResult check(String text, UUID lawyerId) {
    List<ExtractedCitation> extracted = citationExtractor.extract(text, properties.maxCitations());
    List<CitationCheck> checks = new ArrayList<>();
    int courtLookups = 0;
    for (ExtractedCitation citation : extracted) {
      switch (citation.type()) {
        case COURT_CASE -> {
          boolean lookupBudgetLeft = courtLookups < properties.maxCourtCaseLookups();
          checks.add(checkCourtCase(citation, lookupBudgetLeft));
          if (lookupBudgetLeft && courtCaseProviderRegistry.hasAnyEnabled()) {
            courtLookups++;
          }
        }
        case STATUTE -> checks.add(checkStatute(citation));
      }
    }
    checks.forEach(check -> statusCounters.get(check.status()).increment());
    log.info(
        "Citation check for lawyer {}: {} citation(s) ({} court lookups)",
        lawyerId,
        checks.size(),
        courtLookups);
    return CitationCheckResult.of(checks);
  }

  @Transactional(readOnly = true)
  public CitationCheckResult checkResponse(UUID responseId, UUID lawyerId) {
    AiResponse response =
        aiResponseRepository
            .findById(responseId)
            .orElseThrow(() -> new AiResponseNotFoundException(responseId));
    if (!response.getLawyerId().equals(lawyerId)) {
      log.warn(
          "Lawyer {} attempted to check citations of response {} owned by another user",
          lawyerId,
          responseId);
      throw new AiResponseNotFoundException(responseId);
    }
    return check(response.getResult(), lawyerId);
  }

  private CitationCheck checkCourtCase(ExtractedCitation citation, boolean lookupBudgetLeft) {
    String number = citation.core();
    CourtSystem system = CourtCaseNumberParser.detectOrDefault(number, CourtSystem.ARBITR);
    Optional<CourtCaseProvider> provider = courtCaseProviderRegistry.enabledFor(system);
    if (provider.isEmpty()) {
      return citationOf(
          citation,
          number,
          CitationStatus.UNVERIFIED,
          "Проверка через " + system.getDisplayName() + " недоступна");
    }
    if (!lookupBudgetLeft) {
      return citationOf(
          citation,
          number,
          CitationStatus.UNVERIFIED,
          "Превышен лимит проверок дел за один запрос");
    }
    try {
      boolean found = provider.get().fetchCase(number).isPresent();
      return found
          ? citationOf(
              citation,
              number,
              CitationStatus.VERIFIED,
              "Дело найдено в " + system.getDisplayName())
          : citationOf(
              citation,
              number,
              CitationStatus.NOT_FOUND,
              "Дело не найдено в "
                  + system.getDisplayName()
                  + " — проверьте номер, возможна ошибка");
    } catch (RuntimeException e) {
      log.warn("{} lookup failed for case {}: {}", system.getDisplayName(), number, e.getMessage());
      return citationOf(
          citation,
          number,
          CitationStatus.UNVERIFIED,
          "Ошибка обращения к " + system.getDisplayName() + ", повторите позже");
    }
  }

  private CitationCheck checkStatute(ExtractedCitation citation) {
    Optional<LegislationRef> current =
        documentAccess.currentLegislation(citation.core(), citation.actCanonical());
    if (current.isPresent()) {
      LegislationRef ref = current.get();
      String normalized =
          "ст. "
              + citation.core()
              + " "
              + ref.actCanonical()
              + (ref.editionDate() != null
                  ? ", ред. от " + EDITION_DATE_FORMAT.format(ref.editionDate())
                  : "");
      return citationOf(
          citation,
          normalized,
          CitationStatus.VERIFIED,
          "Норма подтверждена по актуальной редакции законодательства");
    }

    String normalized =
        "ст. "
            + citation.core()
            + (citation.actCanonical() != null ? " — " + citation.actCanonical() : "");
    boolean grounded = documentAccess.knowledgeBaseMentions(citation.core());
    String detail;
    if (citation.actCanonical() != null) {
      detail =
          grounded
              ? "Акт распознан ("
                  + citation.actCanonical()
                  + "), но актуальная редакция нормы "
                  + "не загружена в базу законодательства — проверьте по первоисточнику"
              : "Акт распознан ("
                  + citation.actCanonical()
                  + "), но норма не подтверждена "
                  + "актуальной редакцией — проверьте вручную";
    } else {
      detail =
          grounded
              ? "Норма упоминается в базе знаний, но не подтверждена актуальной редакцией — проверьте вручную"
              : "Норма и акт не подтверждены — проверьте вручную";
    }
    return citationOf(citation, normalized, CitationStatus.UNVERIFIED, detail);
  }

  private CitationCheck citationOf(
      ExtractedCitation citation, String normalized, CitationStatus status, String detail) {
    return new CitationCheck(citation.raw(), citation.type(), normalized, status, detail);
  }
}
