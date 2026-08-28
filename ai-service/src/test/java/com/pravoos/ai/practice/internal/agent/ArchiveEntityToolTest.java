package com.pravoos.ai.practice.internal.agent;

import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ADMIN;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.MAPPER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ORG_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.core.api.AiActionProposals;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.ProposedAction;
import com.pravoos.ai.recyclebin.api.DeletionActor;
import com.pravoos.ai.recyclebin.api.DeletionRole;
import com.pravoos.ai.recyclebin.api.RecycleBin;
import com.pravoos.ai.recyclebin.api.RecycleBinEntityType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveEntityToolTest {

  private static final UUID ENTITY_ID = UUID.randomUUID();

  @Mock private AiActionProposals proposals;
  @Mock private RecycleBin recycleBin;

  private ArchiveCaseTool caseTool() {
    return new ArchiveCaseTool(proposals, VALIDATOR, recycleBin, MAPPER);
  }

  private ArchiveClientTool clientTool() {
    return new ArchiveClientTool(proposals, VALIDATOR, recycleBin, MAPPER);
  }

  @Test
  void bothToolsAreWriteToolsOfferedToLawyersOnly() {
    assertThat(caseTool().readOnly()).isFalse();
    assertThat(caseTool().requiresApproval()).isTrue();
    assertThat(caseTool().availableFor(LAWYER)).isTrue();
    assertThat(caseTool().availableFor(ADMIN)).isFalse();
    assertThat(clientTool().availableFor(ADMIN)).isFalse();
  }

  @Test
  void executeOnlyProposesAndNeverTouchesTheRecycleBin() {
    ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
    when(proposals.propose(any(), any(), title.capture(), any()))
        .thenReturn(
            new ProposedAction(
                UUID.randomUUID(), "archive_case", "t", LocalDateTime.now().plusMinutes(30)));

    AiToolResult result = caseTool().execute(arguments("caseId"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(title.getValue()).contains("корзину").contains(ENTITY_ID.toString());
    verify(recycleBin, never()).moveToBin(any(), any(), any(), anyBoolean());
  }

  @Test
  void rejectsAnIdentifierThatIsNotAUuidBeforeCreatingAProposal() {
    AiToolResult result =
        caseTool().execute(MAPPER.createObjectNode().put("caseId", "не-uuid"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("caseId");
    verify(proposals, never()).propose(any(), any(), any(), any());
  }

  @Test
  void archivingACaseMovesItToTheRecycleBinAsTheLawyerWithoutDeletingIt() {
    AiToolResult result = caseTool().perform(arguments("caseId"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("RECYCLE_BIN").contains("CASE");
    ArgumentCaptor<DeletionActor> actor = ArgumentCaptor.forClass(DeletionActor.class);
    verify(recycleBin)
        .moveToBin(
            eq(RecycleBinEntityType.CASE), eq(ENTITY_ID.toString()), actor.capture(), eq(true));
    assertThat(actor.getValue().userId()).isEqualTo(LAWYER_ID);
    assertThat(actor.getValue().role()).isEqualTo(DeletionRole.LAWYER);
    assertThat(actor.getValue().orgId()).isEqualTo(ORG_ID);
    assertThat(actor.getValue().orgIds()).isEqualTo(List.of(ORG_ID));
  }

  @Test
  void archivingAClientMovesTheClientEntityAndReadsItsOwnArgumentName() {
    AiToolResult result = clientTool().perform(arguments("clientId"), LAWYER);

    assertThat(result.ok()).isTrue();
    verify(recycleBin)
        .moveToBin(eq(RecycleBinEntityType.CLIENT), eq(ENTITY_ID.toString()), any(), eq(true));
    assertThat(clientTool().parameters().get("properties").has("clientId")).isTrue();
  }

  @Test
  void eachToolNamesItsOwnIdentifierArgumentAndForbidsUnknownFields() {
    JsonNode caseSchema = caseTool().parameters();

    assertThat(caseSchema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(caseSchema.get("required")).extracting(JsonNode::asText).containsExactly("caseId");
    assertThat(clientTool().parameters().get("required"))
        .extracting(JsonNode::asText)
        .containsExactly("clientId");
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode arguments(String field) {
    return MAPPER.createObjectNode().put(field, ENTITY_ID.toString());
  }
}
