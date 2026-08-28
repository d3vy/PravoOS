package com.pravoos.ai.practice.internal.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.core.api.AiTool;
import com.pravoos.ai.core.api.AiToolContext;
import com.pravoos.ai.core.api.AiToolResult;
import com.pravoos.ai.core.api.InvalidToolArgumentException;
import com.pravoos.ai.core.api.ToolArguments;
import com.pravoos.ai.core.api.ToolJson;
import com.pravoos.ai.core.api.ToolSchema;
import com.pravoos.ai.practice.internal.dto.GlobalSearchResponse;
import com.pravoos.ai.practice.internal.service.SearchService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SearchWorkspaceTool implements AiTool {

  private static final String NOTHING_FOUND = "По этому запросу в рабочем пространстве ничего нет.";
  private static final int MAX_HITS_PER_KIND = 8;
  private static final int SNIPPET_MAX_CHARS = 300;

  private final SearchService searchService;
  private final ObjectMapper objectMapper;

  public SearchWorkspaceTool(SearchService searchService, ObjectMapper objectMapper) {
    this.searchService = searchService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String name() {
    return "search_workspace";
  }

  @Override
  public String description() {
    return "Ищет по рабочему пространству юриста: делам, клиентам, документам, счетам и "
        + "прошлым диалогам. Возвращает идентификаторы, по которым можно запросить карточку "
        + "через get_case, get_client или read_document. Используй, когда пользователь "
        + "называет дело, клиента или документ по имени, а не по идентификатору.";
  }

  @Override
  public JsonNode parameters() {
    return ToolSchema.object(objectMapper)
        .requiredString("query", "Поисковый запрос: часть названия дела, имени клиента или файла")
        .optionalBoolean(
            "searchDocumentContent",
            "Искать ли по содержимому документов, а не только по названиям. По умолчанию нет.")
        .build();
  }

  @Override
  public boolean availableFor(AiToolContext context) {
    return context.isLawyer();
  }

  @Override
  public AiToolResult execute(JsonNode arguments, AiToolContext context) {
    String query;
    try {
      query = ToolArguments.requireText(arguments, "query");
    } catch (InvalidToolArgumentException ex) {
      return AiToolResult.error(ex.getMessage());
    }

    boolean searchContent =
        arguments.hasNonNull("searchDocumentContent")
            && arguments.get("searchDocumentContent").asBoolean(false);
    GlobalSearchResponse found = searchService.search(context.userId(), query, searchContent);
    SearchView view = SearchView.from(found);
    if (view.isEmpty()) {
      return AiToolResult.error(NOTHING_FOUND);
    }
    return AiToolResult.ok(ToolJson.write(objectMapper, view));
  }

  private record SearchView(
      List<CaseHit> cases,
      List<ClientHit> clients,
      List<DocumentHit> documents,
      List<InvoiceHit> invoices) {

    private static SearchView from(GlobalSearchResponse response) {
      return new SearchView(
          response.cases().stream().limit(MAX_HITS_PER_KIND).map(CaseHit::from).toList(),
          response.clients().stream().limit(MAX_HITS_PER_KIND).map(ClientHit::from).toList(),
          response.documents().stream().limit(MAX_HITS_PER_KIND).map(DocumentHit::from).toList(),
          response.invoices().stream().limit(MAX_HITS_PER_KIND).map(InvoiceHit::from).toList());
    }

    private boolean isEmpty() {
      return cases.isEmpty() && clients.isEmpty() && documents.isEmpty() && invoices.isEmpty();
    }
  }

  private record CaseHit(String caseId, String title, String status, String clientName) {

    private static CaseHit from(GlobalSearchResponse.CaseHit hit) {
      return new CaseHit(hit.id().toString(), hit.title(), hit.statusName(), hit.clientName());
    }
  }

  private record ClientHit(String clientId, String name, String email, String phone) {

    private static ClientHit from(GlobalSearchResponse.ClientHit hit) {
      return new ClientHit(hit.id().toString(), hit.name(), hit.email(), hit.phone());
    }
  }

  private record DocumentHit(String documentId, String title, String caseId, String snippet) {

    private static DocumentHit from(GlobalSearchResponse.DocumentHit hit) {
      return new DocumentHit(
          hit.id().toString(),
          hit.title(),
          hit.caseId() == null ? null : hit.caseId().toString(),
          ToolJson.truncate(hit.snippet(), SNIPPET_MAX_CHARS));
    }
  }

  private record InvoiceHit(
      String invoiceId, String number, String clientName, String total, String status) {

    private static InvoiceHit from(GlobalSearchResponse.InvoiceHit hit) {
      return new InvoiceHit(
          hit.id().toString(),
          hit.number(),
          hit.clientName(),
          ToolJson.text(hit.total()),
          hit.statusName());
    }
  }
}
