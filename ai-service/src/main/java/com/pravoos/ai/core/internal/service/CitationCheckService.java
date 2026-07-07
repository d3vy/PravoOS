package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.shared.arbitr.ArbitrCaseProvider;
import com.pravoos.ai.shared.config.CitationCheckProperties;
import com.pravoos.ai.shared.exception.AiResponseNotFoundException;
import com.pravoos.ai.core.internal.dto.CitationCheck;
import com.pravoos.ai.core.internal.dto.CitationCheckResult;
import com.pravoos.ai.core.internal.model.entity.AiResponse;
import com.pravoos.ai.shared.model.enums.CitationStatus;
import com.pravoos.ai.document.api.DocumentAccess;
import com.pravoos.ai.core.internal.repository.jpa.AiResponseRepository;
import com.pravoos.ai.core.internal.service.CitationExtractor.ExtractedCitation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CitationCheckService {

    private static final Logger log = LoggerFactory.getLogger(CitationCheckService.class);

    private final CitationExtractor citationExtractor;
    private final ArbitrCaseProvider arbitrCaseProvider;
    private final DocumentAccess documentAccess;
    private final AiResponseRepository aiResponseRepository;
    private final CitationCheckProperties properties;

    public CitationCheckService(CitationExtractor citationExtractor,
                                ArbitrCaseProvider arbitrCaseProvider,
                                DocumentAccess documentAccess,
                                AiResponseRepository aiResponseRepository,
                                CitationCheckProperties properties) {
        this.citationExtractor = citationExtractor;
        this.arbitrCaseProvider = arbitrCaseProvider;
        this.documentAccess = documentAccess;
        this.aiResponseRepository = aiResponseRepository;
        this.properties = properties;
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
                    if (lookupBudgetLeft && arbitrCaseProvider.isEnabled()) {
                        courtLookups++;
                    }
                }
                case STATUTE -> checks.add(checkStatute(citation));
            }
        }
        log.info("Citation check for lawyer {}: {} citation(s) ({} court lookups)",
                lawyerId, checks.size(), courtLookups);
        return CitationCheckResult.of(checks);
    }

    @Transactional(readOnly = true)
    public CitationCheckResult checkResponse(UUID responseId, UUID lawyerId) {
        AiResponse response = aiResponseRepository.findById(responseId)
                .orElseThrow(() -> new AiResponseNotFoundException(responseId));
        if (!response.getLawyerId().equals(lawyerId)) {
            log.warn("Lawyer {} attempted to check citations of response {} owned by another user",
                    lawyerId, responseId);
            throw new AiResponseNotFoundException(responseId);
        }
        return check(response.getResult(), lawyerId);
    }

    private CitationCheck checkCourtCase(ExtractedCitation citation, boolean lookupBudgetLeft) {
        String number = citation.core();
        if (!arbitrCaseProvider.isEnabled()) {
            return citationOf(citation, number, CitationStatus.UNVERIFIED,
                    "Проверка через КАД.Арбитр недоступна");
        }
        if (!lookupBudgetLeft) {
            return citationOf(citation, number, CitationStatus.UNVERIFIED,
                    "Превышен лимит проверок дел за один запрос");
        }
        try {
            boolean found = arbitrCaseProvider.fetchCase(number).isPresent();
            return found
                    ? citationOf(citation, number, CitationStatus.VERIFIED, "Дело найдено в КАД.Арбитр")
                    : citationOf(citation, number, CitationStatus.NOT_FOUND,
                            "Дело не найдено в КАД.Арбитр — проверьте номер, возможна ошибка");
        } catch (RuntimeException e) {
            log.warn("КАД.Арбитр lookup failed for case {}: {}", number, e.getMessage());
            return citationOf(citation, number, CitationStatus.UNVERIFIED,
                    "Ошибка обращения к КАД.Арбитр, повторите позже");
        }
    }

    private CitationCheck checkStatute(ExtractedCitation citation) {
        String normalized = "ст. " + citation.core()
                + (citation.actCanonical() != null ? " — " + citation.actCanonical() : "");
        boolean grounded = documentAccess.knowledgeBaseMentions(citation.core());
        if (grounded) {
            String detail = citation.actCanonical() != null
                    ? "Норма упоминается в базе знаний; акт распознан"
                    : "Норма упоминается в базе знаний";
            return citationOf(citation, normalized, CitationStatus.VERIFIED, detail);
        }
        String detail = citation.actCanonical() != null
                ? "Акт распознан (" + citation.actCanonical() + "), но норма не найдена в базе знаний — проверьте вручную"
                : "Норма и акт не подтверждены — проверьте вручную";
        return citationOf(citation, normalized, CitationStatus.UNVERIFIED, detail);
    }

    private CitationCheck citationOf(ExtractedCitation citation, String normalized,
                                     CitationStatus status, String detail) {
        return new CitationCheck(citation.raw(), citation.type(), normalized, status, detail);
    }

}
