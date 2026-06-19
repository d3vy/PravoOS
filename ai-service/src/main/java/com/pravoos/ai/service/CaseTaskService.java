package com.pravoos.ai.service;

import com.pravoos.ai.exception.CaseTaskNotFoundException;
import com.pravoos.ai.model.dto.AiResponseDto;
import com.pravoos.ai.model.dto.CaseTaskResponse;
import com.pravoos.ai.model.dto.CreateCaseTaskRequest;
import com.pravoos.ai.model.dto.UpdateCaseTaskRequest;
import com.pravoos.ai.model.entity.CaseTask;
import com.pravoos.ai.model.enums.BankruptcyWorkflow;
import com.pravoos.ai.repository.jpa.CaseTaskRepository;
import com.pravoos.ai.util.ChecklistTableParser;
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
    public CaseTaskResponse create(UUID caseId, CreateCaseTaskRequest request, UUID lawyerId) {
        caseService.requireOwnedCase(caseId, lawyerId);

        CaseTask task = new CaseTask();
        task.setCaseId(caseId);
        task.setText(request.text().trim());
        task.setDueDate(request.dueDate());

        CaseTask saved = caseTaskRepository.save(task);
        log.info("Case task created: {} on case {} by lawyer {}", saved.getId(), caseId, lawyerId);
        return CaseTaskResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseTaskResponse> findByCase(UUID caseId, UUID lawyerId) {
        caseService.requireOwnedCase(caseId, lawyerId);
        return caseTaskRepository.findByCaseIdOrderByDoneAscCreatedAtAsc(caseId)
                .stream()
                .map(CaseTaskResponse::from)
                .toList();
    }

    @Transactional
    public CaseTaskResponse update(UUID caseId, UUID taskId, UpdateCaseTaskRequest request, UUID lawyerId) {
        caseService.requireOwnedCase(caseId, lawyerId);
        CaseTask task = requireTaskInCase(caseId, taskId);

        task.setText(request.text().trim());
        task.setDueDate(request.dueDate());
        task.setDone(request.done());

        log.info("Case task updated: {} on case {} (done={}) by lawyer {}", taskId, caseId, request.done(), lawyerId);
        return CaseTaskResponse.from(task);
    }

    @Transactional
    public void delete(UUID caseId, UUID taskId, UUID lawyerId) {
        caseService.requireOwnedCase(caseId, lawyerId);
        CaseTask task = requireTaskInCase(caseId, taskId);
        caseTaskRepository.delete(task);
        log.info("Case task deleted: {} on case {} by lawyer {}", taskId, caseId, lawyerId);
    }

    @Transactional
    public List<CaseTaskResponse> generateFromChecklist(UUID caseId, UUID lawyerId) {
        caseService.requireOwnedCase(caseId, lawyerId);

        AiResponseDto checklist = workflowService.run(
                caseId, BankruptcyWorkflow.DOCUMENT_CHECKLIST.name(), null, lawyerId);
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
