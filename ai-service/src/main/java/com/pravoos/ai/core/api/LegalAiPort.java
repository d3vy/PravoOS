package com.pravoos.ai.core.api;

import java.util.List;
import java.util.UUID;

public interface LegalAiPort {

  void assertWithinQuota(UUID lawyerId);

  float[] embed(String text);

  LegalAiAnswer answerForCase(
      UUID caseId, String instruction, String userMessage, UUID lawyerId, List<UUID> orgIds);

  LegalAiAnswer refineDraft(
      UUID caseId, String instruction, String currentText, UUID lawyerId, List<UUID> orgIds);

  LegalAiAnswer analyzeCase(
      UUID caseId,
      String caseContext,
      String hearingTimeline,
      String statistics,
      UUID lawyerId,
      List<UUID> orgIds);

  AiResponseDto runCaseWorkflow(
      UUID caseId,
      UUID lawyerId,
      List<UUID> orgIds,
      String workflowId,
      String query,
      String instruction);
}
