package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.practice.internal.dto.SaveWorkflowDefinitionRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowDefinitionDto;
import com.pravoos.ai.practice.internal.dto.WorkflowStepDto;
import com.pravoos.ai.practice.internal.model.WorkflowStepConfig;
import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.practice.internal.repository.jpa.WorkflowDefinitionRepository;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import com.pravoos.ai.shared.exception.DraftTypeNotFoundException;
import com.pravoos.ai.shared.exception.InvalidWorkflowDefinitionException;
import com.pravoos.ai.shared.exception.WorkflowDefinitionNotFoundException;
import com.pravoos.ai.shared.model.enums.DraftType;
import com.pravoos.ai.shared.model.enums.WorkflowStepType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowDefinitionService {

  private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);
  private static final UUID NO_ORG = new UUID(0L, 0L);

  private final WorkflowDefinitionRepository definitionRepository;
  private final RecycleBin recycleBin;
  private final WorkflowDefinitionCatalogCache catalogCache;
  private final boolean catalogCacheEnabled;

  public WorkflowDefinitionService(
      WorkflowDefinitionRepository definitionRepository,
      RecycleBin recycleBin,
      WorkflowDefinitionCatalogCache catalogCache,
      @Value("${ai.catalog-cache.enabled:true}") boolean catalogCacheEnabled) {
    this.definitionRepository = definitionRepository;
    this.recycleBin = recycleBin;
    this.catalogCache = catalogCache;
    this.catalogCacheEnabled = catalogCacheEnabled;
  }

  @Transactional(readOnly = true)
  public List<WorkflowDefinitionDto> listVisible(UUID lawyerId, List<UUID> orgIds) {
    if (!catalogCacheEnabled) {
      return loadVisible(lawyerId, orgIds);
    }
    String key = cacheKey(lawyerId, orgIds);
    return catalogCache.get(key, k -> loadVisible(lawyerId, orgIds));
  }

  private List<WorkflowDefinitionDto> loadVisible(UUID lawyerId, List<UUID> orgIds) {
    return definitionRepository.findVisible(lawyerId, safeOrgIds(orgIds)).stream()
        .map(definition -> WorkflowDefinitionDto.from(definition, isEditable(definition, lawyerId)))
        .toList();
  }

  private static String cacheKey(UUID lawyerId, List<UUID> orgIds) {
    String sortedOrgIds =
        orgIds == null
            ? ""
            : orgIds.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    return lawyerId + "|" + sortedOrgIds;
  }

  @Transactional(readOnly = true)
  public WorkflowDefinitionDto get(UUID id, UUID lawyerId, List<UUID> orgIds) {
    WorkflowDefinition definition = requireVisible(id, lawyerId, orgIds);
    return WorkflowDefinitionDto.from(definition, isEditable(definition, lawyerId));
  }

  @Transactional(readOnly = true)
  public WorkflowDefinition requireVisible(UUID id, UUID lawyerId, List<UUID> orgIds) {
    WorkflowDefinition definition =
        definitionRepository
            .findById(id)
            .orElseThrow(() -> new WorkflowDefinitionNotFoundException(id));
    if (!isVisible(definition, lawyerId, orgIds)) {
      log.warn(
          "Lawyer {} attempted to access workflow definition {} not visible to them", lawyerId, id);
      throw new WorkflowDefinitionNotFoundException(id);
    }
    return definition;
  }

  @Transactional
  public WorkflowDefinitionDto create(SaveWorkflowDefinitionRequest request, UUID lawyerId) {
    WorkflowDefinition definition = new WorkflowDefinition();
    definition.setCreatedBy(lawyerId);
    definition.setSystem(false);
    applyRequest(definition, request);
    WorkflowDefinition saved = definitionRepository.save(definition);
    catalogCache.evictAll();
    log.info(
        "Workflow definition created: {} ({}) by lawyer {}",
        saved.getId(),
        saved.getName(),
        lawyerId);
    return WorkflowDefinitionDto.from(saved, true);
  }

  @Transactional
  public WorkflowDefinitionDto update(
      UUID id, SaveWorkflowDefinitionRequest request, UUID lawyerId, List<UUID> orgIds) {
    WorkflowDefinition definition = requireEditable(id, lawyerId, orgIds);
    applyRequest(definition, request);
    catalogCache.evictAll();
    log.info("Workflow definition updated: {} by lawyer {}", id, lawyerId);
    return WorkflowDefinitionDto.from(definition, true);
  }

  @Transactional
  public void delete(UUID id, DeletionActor actor) {
    requireEditable(id, actor.userId(), actor.orgIds());
    recycleBin.moveToBin(RecycleBinEntityType.WORKFLOW_DEFINITION, id.toString(), actor);
    log.info("Workflow definition deleted: {} by lawyer {}", id, actor.userId());
  }

  private WorkflowDefinition requireEditable(UUID id, UUID lawyerId, List<UUID> orgIds) {
    WorkflowDefinition definition = requireVisible(id, lawyerId, orgIds);
    if (!isEditable(definition, lawyerId)) {
      throw new InvalidWorkflowDefinitionException("Системный процесс нельзя изменить или удалить");
    }
    return definition;
  }

  private void applyRequest(WorkflowDefinition definition, SaveWorkflowDefinitionRequest request) {
    List<WorkflowStepConfig> steps = toValidatedSteps(request.steps());
    definition.setName(request.name().trim());
    definition.setDescription(request.description() != null ? request.description().trim() : null);
    definition.setCategory(request.category());
    definition.setSteps(steps);
  }

  private List<WorkflowStepConfig> toValidatedSteps(List<WorkflowStepDto> stepDtos) {
    List<WorkflowStepConfig> steps =
        new ArrayList<>(
            IntStream.range(0, stepDtos.size())
                .mapToObj(index -> stepDtos.get(index).toConfig(index))
                .toList());
    steps.forEach(this::validateStep);
    return steps;
  }

  private void validateStep(WorkflowStepConfig step) {
    WorkflowStepType type = step.type();
    switch (type) {
      case AI_ANALYSIS, GENERATE_TASKS -> requireInstruction(step);
      case GENERATE_DRAFT -> requireDraftType(step);
      case SET_DEADLINE -> requireDeadline(step);
    }
  }

  private void requireInstruction(WorkflowStepConfig step) {
    if (step.instruction() == null || step.instruction().isBlank()) {
      throw new InvalidWorkflowDefinitionException("Шаг «" + step.title() + "» требует инструкцию");
    }
  }

  private void requireDraftType(WorkflowStepConfig step) {
    if (step.draftType() == null || step.draftType().isBlank()) {
      throw new InvalidWorkflowDefinitionException(
          "Шаг «" + step.title() + "» требует тип черновика");
    }
    try {
      DraftType.fromId(step.draftType());
    } catch (DraftTypeNotFoundException ex) {
      throw new InvalidWorkflowDefinitionException(
          "Неизвестный тип черновика: " + step.draftType());
    }
  }

  private void requireDeadline(WorkflowStepConfig step) {
    if (step.deadlineType() == null || step.deadlineOffsetDays() == null) {
      throw new InvalidWorkflowDefinitionException(
          "Шаг «" + step.title() + "» требует тип и смещение дедлайна");
    }
  }

  private boolean isVisible(WorkflowDefinition definition, UUID lawyerId, List<UUID> orgIds) {
    return definition.isSystem()
        || lawyerId.equals(definition.getCreatedBy())
        || (definition.getOrgId() != null
            && orgIds != null
            && orgIds.contains(definition.getOrgId()));
  }

  private boolean isEditable(WorkflowDefinition definition, UUID lawyerId) {
    return !definition.isSystem() && lawyerId.equals(definition.getCreatedBy());
  }

  private List<UUID> safeOrgIds(List<UUID> orgIds) {
    if (orgIds == null || orgIds.isEmpty()) {
      return List.of(NO_ORG);
    }
    return orgIds;
  }
}
