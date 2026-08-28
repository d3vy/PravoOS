package com.pravoos.ai.practice.internal.agent;

import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ADMIN;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.MAPPER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ORG_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.document.api.DocumentCommand;
import com.pravoos.ai.document.api.DocumentResponse;
import com.pravoos.ai.practice.internal.model.entity.Case;
import com.pravoos.ai.practice.internal.service.CaseService;
import com.pravoos.ai.shared.exception.CaseNotFoundException;
import com.pravoos.ai.shared.exception.DocumentAlreadyLinkedException;
import com.pravoos.ai.shared.model.enums.DocumentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkDocumentToCaseToolTest {

  private static final UUID DOCUMENT_ID = UUID.randomUUID();
  private static final UUID CASE_ID = UUID.randomUUID();

  @Mock private AiActionProposals proposals;
  @Mock private DocumentCommand documentCommand;
  @Mock private CaseService caseService;

  private LinkDocumentToCaseTool tool() {
    return new LinkDocumentToCaseTool(proposals, VALIDATOR, documentCommand, caseService, MAPPER);
  }

  @Test
  void isAWriteToolOfferedToLawyersOnly() {
    assertThat(tool().readOnly()).isFalse();
    assertThat(tool().requiresApproval()).isTrue();
    assertThat(tool().availableFor(LAWYER)).isTrue();
    assertThat(tool().availableFor(ADMIN)).isFalse();
  }

  @Test
  void executeOnlyProposesAndNeverAttachesTheDocument() {
    when(proposals.propose(any(), any(), any(), any()))
        .thenReturn(
            new ProposedAction(
                UUID.randomUUID(),
                "link_document_to_case",
                "t",
                LocalDateTime.now().plusMinutes(30)));

    AiToolResult result = tool().execute(arguments(), LAWYER);

    assertThat(result.ok()).isTrue();
    verify(documentCommand, never()).attachToCase(any(), any());
  }

  @Test
  void rejectsIdentifiersThatAreNotUuidsBeforeCreatingAProposal() {
    ObjectNode arguments =
        MAPPER.createObjectNode().put("documentId", "не-uuid").put("caseId", CASE_ID.toString());

    AiToolResult result = tool().execute(arguments, LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("documentId");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void performChecksCaseVisibilityBeforeAttachingTheDocument() {
    when(caseService.requireVisibleCase(CASE_ID, LAWYER_ID, List.of(ORG_ID)))
        .thenThrow(new CaseNotFoundException(CASE_ID));

    assertThatThrownBy(() -> tool().perform(arguments(), LAWYER))
        .isInstanceOf(CaseNotFoundException.class);
    verify(documentCommand, never()).attachToCase(any(), any());
  }

  @Test
  void performAttachesTheDocumentToTheCase() {
    when(caseService.requireVisibleCase(CASE_ID, LAWYER_ID, List.of(ORG_ID)))
        .thenReturn(new Case());
    when(documentCommand.attachToCase(DOCUMENT_ID, CASE_ID))
        .thenReturn(
            new DocumentResponse(
                DOCUMENT_ID,
                "Договор поставки",
                "dogovor.pdf",
                "application/pdf",
                DocumentStatus.READY,
                LocalDateTime.now(),
                false));

    AiToolResult result = tool().perform(arguments(), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("Договор поставки").contains(CASE_ID.toString());
    verify(documentCommand).attachToCase(eq(DOCUMENT_ID), eq(CASE_ID));
  }

  @Test
  void performDoesNotSwallowARefusalToMoveADocumentBetweenCases() {
    when(caseService.requireVisibleCase(CASE_ID, LAWYER_ID, List.of(ORG_ID)))
        .thenReturn(new Case());
    when(documentCommand.attachToCase(DOCUMENT_ID, CASE_ID))
        .thenThrow(new DocumentAlreadyLinkedException(DOCUMENT_ID, UUID.randomUUID()));

    assertThatThrownBy(() -> tool().perform(arguments(), LAWYER))
        .isInstanceOf(DocumentAlreadyLinkedException.class);
  }

  @Test
  void schemaRequiresBothIdentifiersAndForbidsUnknownFields() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required"))
        .extracting(JsonNode::asText)
        .containsExactlyInAnyOrder("documentId", "caseId");
  }

  private static ObjectNode arguments() {
    return MAPPER
        .createObjectNode()
        .put("documentId", DOCUMENT_ID.toString())
        .put("caseId", CASE_ID.toString());
  }
}
