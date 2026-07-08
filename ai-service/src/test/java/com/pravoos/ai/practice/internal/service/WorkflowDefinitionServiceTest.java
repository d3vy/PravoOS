package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.SaveWorkflowDefinitionRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowDefinitionDto;
import com.pravoos.ai.practice.internal.dto.WorkflowStepDto;
import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.practice.internal.repository.jpa.WorkflowDefinitionRepository;
import com.pravoos.ai.shared.exception.InvalidWorkflowDefinitionException;
import com.pravoos.ai.shared.exception.WorkflowDefinitionNotFoundException;
import com.pravoos.ai.shared.model.enums.WorkflowCategory;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionServiceTest {

    @Mock private WorkflowDefinitionRepository repository;

    private final UUID lawyerId = UUID.randomUUID();

    private WorkflowDefinitionService service() {
        return new WorkflowDefinitionService(repository);
    }

    private SaveWorkflowDefinitionRequest request(WorkflowStepDto... steps) {
        return new SaveWorkflowDefinitionRequest("Процесс", "описание", WorkflowCategory.CUSTOM, List.of(steps));
    }

    @Test
    void createPersistsValidDefinition() {
        when(repository.save(any(WorkflowDefinition.class))).thenAnswer(inv -> inv.getArgument(0));

        WorkflowDefinitionDto dto = service().create(request(
                new WorkflowStepDto(WorkflowStepType.AI_ANALYSIS, "Анализ", "проанализируй", null, null, null)),
                lawyerId);

        assertThat(dto.editable()).isTrue();
        assertThat(dto.steps()).hasSize(1);
        assertThat(dto.steps().get(0).order()).isZero();
    }

    @Test
    void createRejectsAiStepWithoutInstruction() {
        assertThatThrownBy(() -> service().create(request(
                new WorkflowStepDto(WorkflowStepType.AI_ANALYSIS, "Анализ", "  ", null, null, null)), lawyerId))
                .isInstanceOf(InvalidWorkflowDefinitionException.class);
    }

    @Test
    void createRejectsDraftStepWithUnknownType() {
        assertThatThrownBy(() -> service().create(request(
                new WorkflowStepDto(WorkflowStepType.GENERATE_DRAFT, "Черновик", null, "UNKNOWN", null, null)), lawyerId))
                .isInstanceOf(InvalidWorkflowDefinitionException.class);
    }

    @Test
    void updateRejectsSystemDefinition() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition system = new WorkflowDefinition();
        system.setSystem(true);
        system.setCategory(WorkflowCategory.BANKRUPTCY);
        when(repository.findById(id)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service().update(id, request(
                new WorkflowStepDto(WorkflowStepType.AI_ANALYSIS, "Анализ", "x", null, null, null)),
                lawyerId, List.of()))
                .isInstanceOf(InvalidWorkflowDefinitionException.class);
    }

    @Test
    void requireVisibleHidesForeignPersonalDefinition() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition foreign = new WorkflowDefinition();
        foreign.setCreatedBy(UUID.randomUUID());
        foreign.setCategory(WorkflowCategory.CUSTOM);
        when(repository.findById(id)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().requireVisible(id, lawyerId, List.of()))
                .isInstanceOf(WorkflowDefinitionNotFoundException.class);
    }
}
