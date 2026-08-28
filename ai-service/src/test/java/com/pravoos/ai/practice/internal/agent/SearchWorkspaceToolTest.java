package com.pravoos.ai.practice.internal.agent;

import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.ADMIN;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.LAWYER_ID;
import static com.pravoos.ai.practice.internal.agent.AiWriteToolFixtures.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse;
import com.pravoos.ai.practice.internal.service.SearchService;
import com.pravoos.ai.shared.model.enums.CaseStatus;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchWorkspaceToolTest {

  @Mock private SearchService searchService;

  private SearchWorkspaceTool tool() {
    return new SearchWorkspaceTool(searchService, MAPPER);
  }

  @Test
  void isAReadOnlyToolOfferedToLawyersOnly() {
    assertThat(tool().readOnly()).isTrue();
    assertThat(tool().availableFor(LAWYER)).isTrue();
    assertThat(tool().availableFor(ADMIN)).isFalse();
  }

  @Test
  void rejectsABlankQueryWithoutHittingTheSearchService() {
    AiToolResult result = tool().execute(MAPPER.createObjectNode().put("query", "  "), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("query");
    verify(searchService, never()).search(any(), anyString(), anyBoolean());
  }

  @Test
  void searchesByTitlesOnlyUnlessTheModelAsksForDocumentContent() {
    when(searchService.search(eq(LAWYER_ID), eq("Иванов"), anyBoolean())).thenReturn(oneClient());

    tool().execute(MAPPER.createObjectNode().put("query", "Иванов"), LAWYER);

    verify(searchService).search(LAWYER_ID, "Иванов", false);
  }

  @Test
  void passesTheDocumentContentFlagThrough() {
    when(searchService.search(eq(LAWYER_ID), eq("Иванов"), anyBoolean())).thenReturn(oneClient());

    tool()
        .execute(
            MAPPER.createObjectNode().put("query", "Иванов").put("searchDocumentContent", true),
            LAWYER);

    verify(searchService).search(LAWYER_ID, "Иванов", true);
  }

  @Test
  void reportsAnEmptyWorkspaceAsAnErrorSoTheModelStopsGuessing() {
    when(searchService.search(eq(LAWYER_ID), eq("Иванов"), anyBoolean()))
        .thenReturn(
            new GlobalSearchResponse(List.of(), List.of(), List.of(), List.of(), List.of()));

    AiToolResult result = tool().execute(MAPPER.createObjectNode().put("query", "Иванов"), LAWYER);

    assertThat(result.ok()).isFalse();
    assertThat(result.content()).contains("ничего нет");
  }

  @Test
  void returnsIdentifiersTheModelCanFeedToTheOtherTools() {
    UUID clientId = UUID.randomUUID();
    when(searchService.search(eq(LAWYER_ID), eq("Иванов"), anyBoolean()))
        .thenReturn(
            new GlobalSearchResponse(
                List.of(),
                List.of(),
                List.of(),
                List.of(new GlobalSearchResponse.ClientHit(clientId, "Иванов", "i@e.ru", "+7")),
                List.of()));

    AiToolResult result = tool().execute(MAPPER.createObjectNode().put("query", "Иванов"), LAWYER);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("clientId").contains(clientId.toString());
  }

  @Test
  void cutsEachKindOfHitDownToAHandfulSoTheContextStaysSmall() {
    List<GlobalSearchResponse.CaseHit> cases =
        IntStream.range(0, 20)
            .mapToObj(
                index ->
                    new GlobalSearchResponse.CaseHit(
                        UUID.randomUUID(),
                        "Дело " + index,
                        CaseStatus.IN_PROGRESS,
                        "В работе",
                        "Иванов"))
            .toList();
    when(searchService.search(eq(LAWYER_ID), eq("Дело"), anyBoolean()))
        .thenReturn(new GlobalSearchResponse(cases, List.of(), List.of(), List.of(), List.of()));

    AiToolResult result = tool().execute(MAPPER.createObjectNode().put("query", "Дело"), LAWYER);

    assertThat(result.content().split("\"caseId\"", -1)).hasSize(9);
  }

  @Test
  void truncatesDocumentSnippetsInsteadOfDumpingWholeDocuments() {
    when(searchService.search(eq(LAWYER_ID), eq("договор"), anyBoolean()))
        .thenReturn(
            new GlobalSearchResponse(
                List.of(),
                List.of(),
                List.of(
                    new GlobalSearchResponse.DocumentHit(
                        UUID.randomUUID(), "Договор", "d.pdf", null, "щ".repeat(5000))),
                List.of(),
                List.of()));

    AiToolResult result = tool().execute(MAPPER.createObjectNode().put("query", "договор"), LAWYER);

    assertThat(result.content()).hasSizeLessThan(1000).contains("…");
  }

  @Test
  void schemaRequiresOnlyTheQueryAndForbidsUnknownFields() {
    JsonNode schema = tool().parameters();

    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required")).extracting(JsonNode::asText).containsExactly("query");
    assertThat(schema.get("properties").get("searchDocumentContent").get("type").asText())
        .isEqualTo("boolean");
  }

  private static GlobalSearchResponse oneClient() {
    return new GlobalSearchResponse(
        List.of(),
        List.of(),
        List.of(),
        List.of(new GlobalSearchResponse.ClientHit(UUID.randomUUID(), "Иванов", null, null)),
        List.of());
  }
}
