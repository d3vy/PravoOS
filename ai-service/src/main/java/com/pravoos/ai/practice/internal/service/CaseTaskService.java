package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.practice.internal.dto.CaseTaskResponse;
import com.pravoos.ai.practice.internal.dto.CreateCaseTaskRequest;
import com.pravoos.ai.practice.internal.dto.UpdateCaseTaskRequest;
import com.pravoos.ai.practice.internal.model.entity.CaseTask;
import com.pravoos.ai.practice.internal.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.shared.exception.CaseTaskNotFoundException;
import com.pravoos.ai.shared.model.enums.BankruptcyWorkflow;
import com.pravoos.ai.shared.util.ChecklistTableParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CaseTaskService {

    private static final Logger log = LoggerFactory.getLogger(CaseTaskService.class);

    private final CaseTaskRepository caseTaskRepository;
    private final CaseService caseService;
    private final WorkflowService workflowService;

    public CaseTaskService(CaseTaskRepository caseTaskRepository,
                           CaseService caseService,
                           WorkflowService workflowService) {
        this.caseTaskRepository = caseTaskRepository;
        this.caseService = caseService;
        this.workflowService = workflowService;
    }

    @Transactional
    public CaseTaskResponse create(UUID caseId, CreateCaseTaskRequest request, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);

        CaseTask task = new CaseTask();
        task.setCaseId(caseId);
        task.setText(request.text().trim());
        task.setDueDate(request.dueDate());

        CaseTask saved = caseTaskRepository.save(task);
        log.info("Case task created: {} on case {} by lawyer {}", saved.getId(), caseId, lawyerId);
        return CaseTaskResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseTaskResponse> findByCase(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        return caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)
                .stream()
                .map(CaseTaskResponse::from)
                .toList();
    }

    @Transactional
    public CaseTaskResponse update(UUID caseId, UUID taskId, UpdateCaseTaskRequest request,
                                   UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        CaseTask task = requireTaskInCase(caseId, taskId);

        task.setText(request.text().trim());
        task.setDueDate(request.dueDate());
        task.setDone(request.done());

        log.info("Case task updated: {} on case {} (done={}) by lawyer {}", taskId, caseId, request.done(), lawyerId);
        return CaseTaskResponse.from(task);
    }

    @Transactional
    public void delete(UUID caseId, UUID taskId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        CaseTask task = requireTaskInCase(caseId, taskId);
        caseTaskRepository.delete(task);
        log.info("Case task deleted: {} on case {} by lawyer {}", taskId, caseId, lawyerId);
    }

    public List<CaseTaskResponse> generateFromChecklist(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);

        AiResponseDto checklist = workflowService.run(
                caseId, BankruptcyWorkflow.DOCUMENT_CHECKLIST.name(), null, lawyerId, orgIds);
        List<String> extracted = ChecklistTableParser.extractMissingDocumentTasks(checklist.result());

        if (extracted.isEmpty()) {
            log.warn("Checklist for case {} produced no actionable tasks (lawyer {})", caseId, lawyerId);
            return List.of();
        }

        Set<String> existing = caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)
                .stream()
                .filter(task -> !task.isDone())
                .map(task -> task.getText().trim())
                .collect(Collectors.toSet());

        List<CaseTask> created = extracted.stream()
                .filter(text -> !existing.contains(text))
                .map(text -> {
                    CaseTask task = new CaseTask();
                    task.setCaseId(caseId);
                    task.setText(text);
                    return task;
                })
                .toList();

        List<CaseTask> saved = caseTaskRepository.saveAll(created);
        log.info("Generated {} task(s) from checklist for case {} by lawyer {} ({} skipped as duplicates)",
                saved.size(), caseId, lawyerId, extracted.size() - saved.size());
        return saved.stream().map(CaseTaskResponse::from).toList();
    }

    private CaseTask requireTaskInCase(UUID caseId, UUID taskId) {
        CaseTask task = caseTaskRepository.findById(taskId)
                .orElseThrow(() -> new CaseTaskNotFoundException(taskId));
        if (!task.getCaseId().equals(caseId)) {
            log.warn("Task {} does not belong to case {}", taskId, caseId);
            throw new CaseTaskNotFoundException(taskId);
        }
        return task;
    }
}
