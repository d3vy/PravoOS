package com.pravoos.ai.core.api;

import com.pravoos.ai.core.api.AiResponseDto;

import java.util.UUID;

public interface LegalAiPort {

    void assertWithinQuota(UUID lawyerId);

    float[] embed(String text);

    LegalAiAnswer answerForCase(UUID caseId, String instruction, String userMessage, UUID lawyerId);

    AiResponseDto runCaseWorkflow(UUID caseId, UUID lawyerId, String workflowId, String query, String instruction);
}
