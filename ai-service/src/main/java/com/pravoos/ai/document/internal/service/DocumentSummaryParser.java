package com.pravoos.ai.document.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pravoos.ai.shared.config.DocumentSummaryProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentSummaryParser {

  private static final Logger log = LoggerFactory.getLogger(DocumentSummaryParser.class);

  private final ObjectMapper objectMapper;
  private final DocumentSummaryProperties properties;

  public DocumentSummaryParser(ObjectMapper objectMapper, DocumentSummaryProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public DocumentSummaryDraft parse(String rawResponse) {
    String jsonObject = extractJsonObject(rawResponse);
    if (jsonObject == null) {
      log.warn("Document summary response contains no JSON object");
      return DocumentSummaryDraft.EMPTY;
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(jsonObject);
    } catch (Exception e) {
      log.warn("Failed to parse document summary response as JSON: {}", e.getMessage());
      return DocumentSummaryDraft.EMPTY;
    }
    if (!root.isObject()) {
      return DocumentSummaryDraft.EMPTY;
    }

    String summary = truncate(text(root.get("summary")), properties.maxSummaryChars());
    List<String> keyPoints = keyPoints(root.get("keyPoints"));
    if (summary.isBlank() && keyPoints.isEmpty()) {
      return DocumentSummaryDraft.EMPTY;
    }
    return new DocumentSummaryDraft(summary, keyPoints);
  }

  private List<String> keyPoints(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    for (JsonNode element : node) {
      String point = truncate(text(element), properties.maxKeyPointChars());
      if (!point.isBlank()) {
        unique.add(point);
      }
      if (unique.size() >= properties.maxKeyPoints()) {
        break;
      }
    }
    return List.copyOf(new ArrayList<>(unique));
  }

  private String text(JsonNode node) {
    if (node == null || node.isNull()) {
      return "";
    }
    return node.isValueNode() ? node.asText().strip() : "";
  }

  private String truncate(String value, int maxChars) {
    if (maxChars <= 0 || value.length() <= maxChars) {
      return value;
    }
    return value.substring(0, maxChars).strip();
  }

  private String extractJsonObject(String rawResponse) {
    if (rawResponse == null || rawResponse.isBlank()) {
      return null;
    }
    int start = rawResponse.indexOf('{');
    int end = rawResponse.lastIndexOf('}');
    if (start < 0 || end <= start) {
      return null;
    }
    return rawResponse.substring(start, end + 1);
  }
}
