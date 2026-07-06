package com.pravoos.ai.core.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.CaseAccessProvider;
import com.pravoos.ai.core.api.DocumentAccess;
import com.pravoos.ai.core.api.DocumentRef;
import com.pravoos.ai.shared.config.ContractReviewProperties;
import com.pravoos.ai.shared.exception.ContractReviewFailedException;
import com.pravoos.ai.shared.exception.DocumentNotFoundException;
import com.pravoos.ai.llm.api.LlmClient;
import com.pravoos.ai.llm.api.LlmResult;
import com.pravoos.ai.llm.api.LlmUsage;
import com.pravoos.ai.core.internal.dto.ContractReviewDto;
import com.pravoos.ai.core.internal.model.entity.ContractReview;
import com.pravoos.ai.shared.model.enums.ContractRiskLevel;
import com.pravoos.ai.core.internal.repository.jpa.ContractReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractReviewServiceTest {

    @Mock private CaseAccessProvider caseAccessProvider;
    @Mock private DocumentAccess documentAccess;
    @Mock private LlmClient llmClient;
    @Mock private LlmQuotaService llmQuotaService;
    @Mock private ContractReviewRepository contractReviewRepository;

    private ContractReviewService service;

    @BeforeEach
    void setUp() {
        ContractReviewProperties properties = new ContractReviewProperties(40000, 30);
        service = new ContractReviewService(caseAccessProvider, documentAccess, llmClient,
                llmQuotaService, contractReviewRepository, new ContractReviewPrompt(),
                properties, new ObjectMapper());
    }

    @Test
    void parsesRisksAndComputesHighRiskCount() {
        DocumentRef document = caseDocument();
        UUID lawyerId = UUID.randomUUID();
        when(documentAccess.findForReview(document.id())).thenReturn(document);
        when(documentAccess.extractText(document.id())).thenReturn("Договор поставки...");
        when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(new LlmResult("""
                {"summary":"Договор с перекосом в пользу поставщика.","riskScore":72,"risks":[
                  {"clause":"Пеня 5% в день","category":"Ответственность","level":"HIGH","explanation":"Чрезмерная неустойка","recommendation":"Снизить до 0.1%"},
                  {"clause":"Односторонний отказ","category":"Расторжение","level":"MEDIUM","explanation":"Риск","recommendation":"Симметрия"}
                ]}
                """, new LlmUsage(100, 200, 300)));
        when(contractReviewRepository.save(any(ContractReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractReviewDto dto = service.review(document.id(), lawyerId, List.of());

        assertThat(dto.riskScore()).isEqualTo((short) 72);
        assertThat(dto.findings()).hasSize(2);
        assertThat(dto.highRiskCount()).isEqualTo(1);
        assertThat(dto.findings().get(0).level()).isEqualTo(ContractRiskLevel.HIGH);
        verify(llmQuotaService).recordUsage(lawyerId, 300L);
    }

    @Test
    void extractsJsonWhenModelWrapsInMarkdown() {
        DocumentRef document = caseDocument();
        when(documentAccess.findForReview(document.id())).thenReturn(document);
        when(documentAccess.extractText(document.id())).thenReturn("Договор");
        when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(new LlmResult(
                "```json\n{\"summary\":\"Ок\",\"riskScore\":10,\"risks\":[]}\n```", new LlmUsage(1, 1, 2)));
        when(contractReviewRepository.save(any(ContractReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractReviewDto dto = service.review(document.id(), UUID.randomUUID(), List.of());

        assertThat(dto.riskScore()).isEqualTo((short) 10);
        assertThat(dto.findings()).isEmpty();
    }

    @Test
    void rejectsNonCaseDocument() {
        UUID documentId = UUID.randomUUID();
        when(documentAccess.findForReview(documentId))
                .thenReturn(new DocumentRef(documentId, null, "Без дела"));

        assertThatThrownBy(() -> service.review(documentId, UUID.randomUUID(), List.of()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void failsWhenModelReturnsNoJson() {
        DocumentRef document = caseDocument();
        when(documentAccess.findForReview(document.id())).thenReturn(document);
        when(documentAccess.extractText(document.id())).thenReturn("Договор");
        when(llmClient.complete(anyString(), anyList(), anyString()))
                .thenReturn(new LlmResult("Извините, не могу.", new LlmUsage(1, 1, 2)));

        assertThatThrownBy(() -> service.review(document.id(), UUID.randomUUID(), List.of()))
                .isInstanceOf(ContractReviewFailedException.class);
    }

    private DocumentRef caseDocument() {
        return new DocumentRef(UUID.randomUUID(), UUID.randomUUID(), "Договор поставки");
    }
}
