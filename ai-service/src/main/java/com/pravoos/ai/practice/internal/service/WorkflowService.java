package com.pravoos.ai.practice.internal.service;

import com.pravoos.ai.core.api.AiResponseDto;
import com.pravoos.ai.core.api.LegalAiPort;
import com.pravoos.ai.practice.internal.dto.RunWorkflowRequest;
import com.pravoos.ai.practice.internal.dto.WorkflowInfo;
import com.pravoos.ai.shared.model.enums.BankruptcyWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final CaseService caseService;
    private final LegalAiPort legalAiPort;

    public WorkflowService(CaseService caseService, LegalAiPort legalAiPort) {
        this.caseService = caseService;
        this.legalAiPort = legalAiPort;
    }

    public List<WorkflowInfo> listWorkflows() {
        return Arrays.stream(BankruptcyWorkflow.values())
                .map(WorkflowInfo::from)
                .toList();
    }

    public AiResponseDto run(UUID caseId, String workflowId, RunWorkflowRequest request,
                             UUID lawyerId, List<UUID> orgIds) {
        legalAiPort.assertWithinQuota(lawyerId);
        caseService.requireVisibleCase(caseId, lawyerId, orgIds);
        BankruptcyWorkflow workflow = BankruptcyWorkflow.fromId(workflowId);

        String question = request != null && request.question() != null ? request.question().trim() : null;
        String instruction = buildInstruction(workflow, question);
        log.info("Running workflow {} on case {} by lawyer {}", workflow.name(), caseId, lawyerId);

        String query = question != null && !question.isBlank() ? question : workflow.displayName();
        AiResponseDto response = legalAiPort.runCaseWorkflow(caseId, lawyerId, workflow.name(), query, instruction);
        log.info("Workflow {} produced response {} with {} source(s)",
                workflow.name(), response.id(), response.sources().size());
        return response;
    }

    private String buildInstruction(BankruptcyWorkflow workflow, String question) {
        if (question == null || question.isBlank()) {
            return workflow.instruction();
        }
        return workflow.instruction() + "\n\nДополнительный вопрос юриста: " + question;
    }
}
