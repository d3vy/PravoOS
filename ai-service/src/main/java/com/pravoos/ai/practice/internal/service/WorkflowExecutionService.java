package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.CaseDraftDto;
import com.pravoos.ai.practice.internal.dto.CaseTaskResponse;
import com.pravoos.ai.practice.internal.dto.GenerateDraftRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowRunDto;
import com.pravoos.ai.practice.internal.model.WorkflowStepConfig;
import com.pravoos.ai.practice.internal.model.WorkflowStepRun;
import com.pravoos.ai.practice.internal.model.entity.WorkflowDefinition;
import com.pravoos.ai.practice.internal.model.entity.WorkflowRun;
import com.pravoos.ai.practice.internal.repository.jpa.WorkflowRunRepository;
import com.pravoos.ai.shared.exception.WorkflowNotFoundException;
import com.pravoos.ai.shared.model.enums.WorkflowRunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);
    private static final int DETAIL_MAX_LENGTH = 500;
    private static final int WORKFLOW_ID_MAX_LENGTH = 100;

    private final WorkflowDefinitionService definitionService;
    private final WorkflowRunRepository runRepository;
    private final CaseService caseService;
    private final DraftService draftService;
    private final CaseTaskService caseTaskService;
    private final LegalAiPort legalAiPort;

    public WorkflowExecutionService(WorkflowDefinitionService definitionService,
                                    WorkflowRunRepository runRepository,
                                    CaseService caseService,
                                    DraftService draftService,
                                    CaseTaskService caseTaskService,
                                    LegalAiPort legalAiPort) {
        this.definitionService = definitionService;
        this.runRepository = runRepository;
        this.caseService = caseService;
        this.draftService = draftService;
        this.caseTaskService = caseTaskService;
        this.legalAiPort = legalAiPort;
    }

    public WorkflowRunDto run(UUID caseId, UUID definitionId, UUID lawyerId, List<UUID> orgIds) {
        legalAiPort.assertWithinQuota(lawyerId);
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        WorkflowDefinition definition = definitionService.requireVisible(definitionId, lawyerId, orgIds);

        List<WorkflowStepConfig> configs = definition.getSteps();
        List<WorkflowStepRun> stepRuns = new ArrayList<>(configs.stream().map(WorkflowStepRun::pending).toList());

        WorkflowRun run = new WorkflowRun();
        run.setCaseId(caseId);
        run.setDefinitionId(definitionId);
        run.setDefinitionName(definition.getName());
        run.setCategory(definition.getCategory());
        run.setLawyerId(lawyerId);
        run.setStatus(WorkflowRunStatus.RUNNING);
        run.setSteps(stepRuns);
        WorkflowRun saved = runRepository.save(run);

        log.info("Workflow run {} started: definition {} on case {} by lawyer {} ({} step(s))",
                saved.getId(), definitionId, caseId, lawyerId, configs.size());

        boolean failed = false;
        for (int i = 0; i < configs.size(); i++) {
            WorkflowStepConfig config = configs.get(i);
            try {
                stepRuns.set(i, executeStep(caseId, config, lawyerId, orgIds));
            } catch (Exception e) {
                log.error("Workflow run {} step {} ({}) failed: {}",
                        saved.getId(), i, config.title(), e.getMessage(), e);
                stepRuns.set(i, stepRuns.get(i).failed(shortMessage(e)));
                failed = true;
                break;
            }
        }

        saved.setSteps(stepRuns);
        saved.setStatus(failed ? WorkflowRunStatus.FAILED : WorkflowRunStatus.COMPLETED);
        saved.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
        WorkflowRun finished = runRepository.save(saved);
        log.info("Workflow run {} finished with status {}", finished.getId(), finished.getStatus());
        return WorkflowRunDto.from(finished);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunDto> listRuns(UUID caseId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        return runRepository.findByCaseIdOrderByStartedAtDesc(caseId).stream()
                .map(WorkflowRunDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowRunDto getRun(UUID caseId, UUID runId, UUID lawyerId, List<UUID> orgIds) {
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        WorkflowRun run = runRepository.findById(runId)
                .orElseThrow(() -> new WorkflowNotFoundException(runId.toString()));
        if (!run.getCaseId().equals(caseId)) {
            throw new WorkflowNotFoundException(runId.toString());
        }
        return WorkflowRunDto.from(run);
    }

    private WorkflowStepRun executeStep(UUID caseId, WorkflowStepConfig config, UUID lawyerId, List<UUID> orgIds) {
        WorkflowStepRun step = WorkflowStepRun.pending(config);
        return switch (config.type()) {
            case AI_ANALYSIS -> {
                legalAiPort.assertWithinQuota(lawyerId);
                AiResponseDto response = legalAiPort.runCaseWorkflow(
                        caseId, lawyerId, workflowId(config), config.title(), config.instruction());
                yield step.completed(truncate(response.result()), response.id(), null);
            }
            case GENERATE_DRAFT -> {
                CaseDraftDto draft = draftService.generate(
                        caseId, new GenerateDraftRequest(config.draftType()), lawyerId, orgIds);
                yield step.completed("Черновик создан: " + draft.title(), null, draft.id());
            }
            case GENERATE_TASKS -> {
                legalAiPort.assertWithinQuota(lawyerId);
                AiResponseDto checklist = legalAiPort.runCaseWorkflow(
                        caseId, lawyerId, workflowId(config), config.title(), config.instruction());
                List<CaseTaskResponse> tasks = caseTaskService.createFromChecklist(
                        caseId, checklist.result(), lawyerId);
                yield step.completed("Создано задач: " + tasks.size(), checklist.id(), null);
            }
            case SET_DEADLINE -> {
                LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(config.deadlineOffsetDays());
                boolean applied = caseService.setDeadlineIfAbsent(
                        caseId, config.deadlineType(), date, lawyerId, orgIds);
                yield applied
                        ? step.completed(config.deadlineType().getDisplayName() + ": " + date, null, null)
                        : step.skipped("Дедлайн уже задан — пропущено");
            }
        };
    }

    private String workflowId(WorkflowStepConfig config) {
        String title = config.title() != null ? config.title() : config.type().name();
        return title.length() > WORKFLOW_ID_MAX_LENGTH ? title.substring(0, WORKFLOW_ID_MAX_LENGTH) : title;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= DETAIL_MAX_LENGTH ? value : value.substring(0, DETAIL_MAX_LENGTH) + "...";
    }

    private String shortMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() <= DETAIL_MAX_LENGTH ? message : message.substring(0, DETAIL_MAX_LENGTH) + "...";
    }
}
