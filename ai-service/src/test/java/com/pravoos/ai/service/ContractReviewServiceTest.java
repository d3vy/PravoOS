package com.pravoos.ai.service;

import com.pravoos.ai.core.internal.service.FileCryptoService;

import com.pravoos.ai.core.internal.service.ContractReviewPrompt;

import com.pravoos.ai.core.internal.service.ContractReviewService;

import com.pravoos.ai.core.internal.service.LlmQuotaService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.config.ContractReviewProperties;
import com.pravoos.ai.exception.ContractReviewFailedException;
import com.pravoos.ai.exception.DocumentNotFoundException;
import com.pravoos.ai.core.internal.llm.LlmClient;
import com.pravoos.ai.core.internal.llm.LlmResult;
import com.pravoos.ai.core.internal.llm.LlmUsage;
import com.pravoos.ai.model.dto.ContractReviewDto;
import com.pravoos.ai.core.internal.model.entity.ContractReview;
import com.pravoos.ai.core.internal.model.entity.Document;
import com.pravoos.ai.model.enums.ContractRiskLevel;
import com.pravoos.ai.core.internal.pipeline.DocumentParser;
import com.pravoos.ai.core.internal.repository.jpa.ContractReviewRepository;
import com.pravoos.ai.core.internal.repository.jpa.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractReviewServiceTest {

    @Mock private CaseService caseService;
    @Mock private DocumentRepository documentRepository;
    @Mock private FileCryptoService fileCryptoService;
    @Mock private DocumentParser documentParser;
    @Mock private LlmClient llmClient;
    @Mock private LlmQuotaService llmQuotaService;
    @Mock private ContractReviewRepository contractReviewRepository;

    private ContractReviewService service;

    @BeforeEach
    void setUp() {
        ContractReviewProperties properties = new ContractReviewProperties(40000, 30);
        service = new ContractReviewService(caseService, documentRepository, fileCryptoService,
                documentParser, llmClient, llmQuotaService, contractReviewRepository,
                new ContractReviewPrompt(), properties, new ObjectMapper());
    }

    @Test
    void parsesRisksAndComputesHighRiskCount() {
        Document document = caseDocument();
        UUID lawyerId = UUID.randomUUID();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(fileCryptoService.decryptFile(any())).thenReturn("contract".getBytes());
        when(documentParser.extractText(any(), eq("pdf"))).thenReturn("Договор поставки...");
        when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(new LlmResult("""
                {"summary":"Договор с перекосом в пользу поставщика.","riskScore":72,"risks":[
                  {"clause":"Пеня 5% в день","category":"Ответственность","level":"HIGH","explanation":"Чрезмерная неустойка","recommendation":"Снизить до 0.1%"},
                  {"clause":"Односторонний отказ","category":"Расторжение","level":"MEDIUM","explanation":"Риск","recommendation":"Симметрия"}
                ]}
                """, new LlmUsage(100, 200, 300)));
        when(contractReviewRepository.save(any(ContractReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractReviewDto dto = service.review(document.getId(), lawyerId, List.of());

        assertThat(dto.riskScore()).isEqualTo((short) 72);
        assertThat(dto.findings()).hasSize(2);
        assertThat(dto.highRiskCount()).isEqualTo(1);
        assertThat(dto.findings().get(0).level()).isEqualTo(ContractRiskLevel.HIGH);
        verify(llmQuotaService).recordUsage(lawyerId, 300L);
    }

    @Test
    void extractsJsonWhenModelWrapsInMarkdown() {
        Document document = caseDocument();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(fileCryptoService.decryptFile(any())).thenReturn("contract".getBytes());
        when(documentParser.extractText(any(), eq("pdf"))).thenReturn("Договор");
        when(llmClient.complete(anyString(), anyList(), anyString())).thenReturn(new LlmResult(
                "```json\n{\"summary\":\"Ок\",\"riskScore\":10,\"risks\":[]}\n```", new LlmUsage(1, 1, 2)));
        when(contractReviewRepository.save(any(ContractReview.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractReviewDto dto = service.review(document.getId(), UUID.randomUUID(), List.of());

        assertThat(dto.riskScore()).isEqualTo((short) 10);
        assertThat(dto.findings()).isEmpty();
    }

    @Test
    void rejectsNonCaseDocument() {
        Document document = new Document();
        document.setFileType("pdf");
        UUID documentId = UUID.randomUUID();
        Document spy = mock(Document.class);
        lenient().when(spy.getId()).thenReturn(documentId);
        lenient().when(spy.getCaseId()).thenReturn(null);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(spy));

        assertThatThrownBy(() -> service.review(documentId, UUID.randomUUID(), List.of()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void failsWhenModelReturnsNoJson() {
        Document document = caseDocument();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(fileCryptoService.decryptFile(any())).thenReturn("contract".getBytes());
        when(documentParser.extractText(any(), eq("pdf"))).thenReturn("Договор");
        when(llmClient.complete(anyString(), anyList(), anyString()))
                .thenReturn(new LlmResult("Извините, не могу.", new LlmUsage(1, 1, 2)));

        assertThatThrownBy(() -> service.review(document.getId(), UUID.randomUUID(), List.of()))
                .isInstanceOf(ContractReviewFailedException.class);
    }

    private Document caseDocument() {
        Document document = mock(Document.class);
        UUID id = UUID.randomUUID();
        lenient().when(document.getId()).thenReturn(id);
        lenient().when(document.getCaseId()).thenReturn(UUID.randomUUID());
        lenient().when(document.getFileType()).thenReturn("pdf");
        lenient().when(document.getFilePath()).thenReturn(existingFilePath());
        lenient().when(document.getTitle()).thenReturn("Договор поставки");
        return document;
    }

    private String existingFilePath() {
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("contract-review-test", ".pdf");
            tmp.toFile().deleteOnExit();
            return tmp.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
