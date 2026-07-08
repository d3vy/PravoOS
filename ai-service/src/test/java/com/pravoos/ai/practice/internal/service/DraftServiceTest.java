package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.LegalAiAnswer;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.GenerateDraftRequest;
import com.pravoos.ai.practice.internal.dto.RefineDraftRequest;
import com.pravoos.ai.practice.internal.dto.RefineDraftResponse;
import com.pravoos.ai.practice.internal.dto.UpdateDraftRequest;
import com.pravoos.ai.practice.internal.model.entity.CaseDraft;
import com.pravoos.ai.practice.internal.model.entity.CaseDraftVersion;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftRepository;
import com.pravoos.ai.practice.internal.repository.jpa.CaseDraftVersionRepository;
import com.pravoos.ai.shared.config.DraftEditingProperties;
import com.pravoos.ai.shared.exception.DraftEditingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    @Mock private CaseService caseService;
    @Mock private LegalAiPort legalAiPort;
    @Mock private CaseDraftRepository caseDraftRepository;
    @Mock private CaseDraftVersionRepository caseDraftVersionRepository;

    private final UUID caseId = UUID.randomUUID();
    private final UUID draftId = UUID.randomUUID();
    private final UUID lawyerId = UUID.randomUUID();

    private DraftService service;

    @BeforeEach
    void setUp() {
        service = new DraftService(caseService, legalAiPort, caseDraftRepository,
                caseDraftVersionRepository, new DraftEditingProperties(1000, 500));
        lenient().when(caseDraftRepository.save(any(CaseDraft.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(caseDraftVersionRepository.maxVersionNo(any())).thenReturn(0);
    }

    private CaseDraft existingDraft(String content) {
        CaseDraft draft = new CaseDraft();
        draft.setCaseId(caseId);
        draft.setLawyerId(lawyerId);
        draft.setDraftType("STATEMENT");
        draft.setTitle("Исковое заявление");
        draft.setContent(content);
        when(caseDraftRepository.findById(draftId)).thenReturn(Optional.of(draft));
        return draft;
    }

    @Test
    void generateSnapshotsInitialVersion() {
        when(legalAiPort.answerForCase(eq(caseId), any(), any(), eq(lawyerId)))
                .thenReturn(new LegalAiAnswer("сгенерированный текст", 10));

        service.generate(caseId, new GenerateDraftRequest("STATEMENT"), lawyerId, List.of());

        ArgumentCaptor<CaseDraftVersion> captor = ArgumentCaptor.forClass(CaseDraftVersion.class);
        verify(caseDraftVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(1);
        assertThat(captor.getValue().getNote()).isEqualTo("Исходная генерация");
        assertThat(captor.getValue().getContent()).isEqualTo("сгенерированный текст");
    }

    @Test
    void updateContentUpdatesDraftAndSnapshotsVersion() {
        CaseDraft draft = existingDraft("старый текст");
        when(caseDraftVersionRepository.maxVersionNo(any())).thenReturn(2);

        CaseDraftDto result = service.updateContent(draftId,
                new UpdateDraftRequest("новый текст", "правка пункта 3"), lawyerId, List.of());

        assertThat(draft.getContent()).isEqualTo("новый текст");
        assertThat(result.content()).isEqualTo("новый текст");
        ArgumentCaptor<CaseDraftVersion> captor = ArgumentCaptor.forClass(CaseDraftVersion.class);
        verify(caseDraftVersionRepository).save(captor.capture());
        assertThat(captor.getValue().getVersionNo()).isEqualTo(3);
        assertThat(captor.getValue().getNote()).isEqualTo("правка пункта 3");
    }

    @Test
    void updateRejectsOversizedContent() {
        existingDraft("текст");
        String tooLong = "x".repeat(1001);

        assertThatThrownBy(() -> service.updateContent(draftId,
                new UpdateDraftRequest(tooLong, null), lawyerId, List.of()))
                .isInstanceOf(DraftEditingException.class);
    }

    @Test
    void restoreVersionReplacesContentAndSnapshots() {
        CaseDraft draft = existingDraft("актуальный текст");
        CaseDraftVersion version = new CaseDraftVersion();
        version.setDraftId(draftId);
        version.setVersionNo(1);
        version.setContent("старая версия");
        UUID versionId = UUID.randomUUID();
        when(caseDraftVersionRepository.findByIdAndDraftId(versionId, draftId)).thenReturn(Optional.of(version));

        service.restoreVersion(draftId, versionId, lawyerId, List.of());

        assertThat(draft.getContent()).isEqualTo("старая версия");
        verify(caseDraftVersionRepository).save(any(CaseDraftVersion.class));
    }

    @Test
    void refineUsesSelectionWhenProvided() {
        existingDraft("полный текст документа");
        when(legalAiPort.refineDraft(eq(caseId), eq("усилить"), eq("фрагмент"), eq(lawyerId)))
                .thenReturn(new LegalAiAnswer("усиленный фрагмент", 5));

        RefineDraftResponse response = service.refine(draftId,
                new RefineDraftRequest("усилить", "фрагмент"), lawyerId, List.of());

        assertThat(response.revisedText()).isEqualTo("усиленный фрагмент");
        verify(legalAiPort).refineDraft(eq(caseId), eq("усилить"), eq("фрагмент"), eq(lawyerId));
    }

    @Test
    void refineUsesFullContentWhenNoSelection() {
        existingDraft("полный текст документа");
        when(legalAiPort.refineDraft(eq(caseId), eq("упростить"), eq("полный текст документа"), eq(lawyerId)))
                .thenReturn(new LegalAiAnswer("упрощённый текст", 5));

        service.refine(draftId, new RefineDraftRequest("упростить", null), lawyerId, List.of());

        verify(legalAiPort).refineDraft(eq(caseId), eq("упростить"), eq("полный текст документа"), eq(lawyerId));
    }
}
