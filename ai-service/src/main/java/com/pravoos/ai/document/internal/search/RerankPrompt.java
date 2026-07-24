package com.pravoos.ai.document.internal.search;

import java.util.List;

final class RerankPrompt {

  static final String SYSTEM_PROMPT =
      """
            Ты — ранжировщик фрагментов юридических документов.
            Для каждого фрагмента оцени, насколько он полезен для ответа на вопрос юриста, по шкале от 0 до 10:
            10 — фрагмент прямо отвечает на вопрос (нужная статья, норма, условие договора);
            5 — фрагмент относится к теме, но не отвечает напрямую;
            0 — фрагмент не относится к вопросу.
            Точное совпадение номера статьи или названия акта из вопроса — сильный признак релевантности.
            Верни ТОЛЬКО JSON-массив без пояснений и без markdown, формат:
            [{"id":1,"score":8},{"id":2,"score":3}]
            Оцени каждый фрагмент ровно один раз.
            """;

  private RerankPrompt() {}

  static String buildUserMessage(
      String query, List<ChunkCandidate> candidates, int maxCharsPerCandidate) {
    StringBuilder message =
        new StringBuilder("Вопрос юриста:\n").append(query).append("\n\nФрагменты:\n");
    for (int i = 0; i < candidates.size(); i++) {
      ChunkCandidate candidate = candidates.get(i);
      message
          .append("[id=")
          .append(i + 1)
          .append("] ")
          .append(sourceLabel(candidate))
          .append('\n')
          .append(truncate(candidate.content(), maxCharsPerCandidate))
          .append("\n\n");
    }
    return message.toString();
  }

  private static String sourceLabel(ChunkCandidate candidate) {
    if (candidate.legislation()) {
      StringBuilder label = new StringBuilder();
      if (candidate.articleNumber() != null && !candidate.articleNumber().isBlank()) {
        label.append("ст. ").append(candidate.articleNumber()).append(' ');
      }
      if (candidate.actCanonical() != null && !candidate.actCanonical().isBlank()) {
        label.append(candidate.actCanonical());
      }
      if (!label.isEmpty()) {
        return label.toString();
      }
    }
    return candidate.documentTitle() == null ? "" : candidate.documentTitle();
  }

  private static String truncate(String content, int maxChars) {
    if (content == null) {
      return "";
    }
    return content.length() <= maxChars ? content : content.substring(0, maxChars) + "...";
  }
}
