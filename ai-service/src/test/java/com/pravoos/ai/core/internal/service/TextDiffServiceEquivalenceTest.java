package com.pravoos.ai.core.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.core.internal.dto.DiffChange;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Закрепляет, что оптимизированное выравнивание (нормализация токенов один раз, срезка общего
 * префикса и суффикса) даёт ровно тот же результат, что прямолинейный LCS по всей матрице.
 */
class TextDiffServiceEquivalenceTest {

  private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\r?\\n");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private final TextDiffService service = new TextDiffService();

  @Test
  void optimizedAlignmentMatchesTheReferenceOnRandomDocuments() {
    Random random = new Random(20260808L);
    for (int iteration = 0; iteration < 300; iteration++) {
      String base = randomDocument(random);
      String revised = mutate(base, random);

      assertThat(describe(service.diff(base, revised)))
          .as("iteration %d%nbase=%s%nrevised=%s", iteration, base, revised)
          .isEqualTo(describe(referenceDiff(base, revised)));
    }
  }

  private String randomDocument(Random random) {
    int paragraphs = random.nextInt(14);
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < paragraphs; i++) {
      lines.add("Пункт " + random.nextInt(6));
    }
    return String.join("\n", lines);
  }

  private String mutate(String document, Random random) {
    List<String> lines = new ArrayList<>(List.of(PARAGRAPH_SPLIT.split(document)));
    int edits = random.nextInt(5);
    for (int i = 0; i < edits; i++) {
      int operation = random.nextInt(3);
      if (operation == 0 || lines.isEmpty()) {
        lines.add(random.nextInt(lines.size() + 1), "Пункт " + random.nextInt(6));
      } else if (operation == 1) {
        lines.remove(random.nextInt(lines.size()));
      } else {
        lines.set(random.nextInt(lines.size()), "Пункт " + random.nextInt(6));
      }
    }
    return String.join("\n", lines);
  }

  private String describe(List<DiffChange> changes) {
    return changes.stream()
        .map(c -> c.order() + "|" + c.type() + "|" + c.baseText() + "|" + c.revisedText())
        .reduce("", (left, right) -> left + "\n" + right);
  }

  private List<DiffChange> referenceDiff(String baseText, String revisedText) {
    List<String> base = splitParagraphs(baseText);
    List<String> revised = splitParagraphs(revisedText);
    int n = base.size();
    int m = revised.size();
    int[][] lcs = new int[n + 1][m + 1];
    for (int i = n - 1; i >= 0; i--) {
      for (int j = m - 1; j >= 0; j--) {
        lcs[i][j] =
            equalToken(base.get(i), revised.get(j))
                ? lcs[i + 1][j + 1] + 1
                : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
      }
    }

    List<String> deleted = new ArrayList<>();
    List<String> inserted = new ArrayList<>();
    List<DiffChange> changes = new ArrayList<>();
    int order = 0;
    int i = 0;
    int j = 0;
    while (i < n && j < m) {
      if (equalToken(base.get(i), revised.get(j))) {
        order = flush(changes, deleted, inserted, order);
        i++;
        j++;
      } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
        deleted.add(base.get(i++));
      } else {
        inserted.add(revised.get(j++));
      }
    }
    while (i < n) {
      deleted.add(base.get(i++));
    }
    while (j < m) {
      inserted.add(revised.get(j++));
    }
    flush(changes, deleted, inserted, order);
    return changes;
  }

  private int flush(
      List<DiffChange> changes, List<String> deleted, List<String> inserted, int order) {
    if (deleted.isEmpty() && inserted.isEmpty()) {
      return order;
    }
    var type =
        deleted.isEmpty()
            ? com.pravoos.ai.shared.model.enums.DiffChangeType.ADDED
            : inserted.isEmpty()
                ? com.pravoos.ai.shared.model.enums.DiffChangeType.REMOVED
                : com.pravoos.ai.shared.model.enums.DiffChangeType.MODIFIED;
    changes.add(
        new DiffChange(
            order,
            type,
            String.join("\n", deleted),
            String.join("\n", inserted),
            null,
            null,
            List.of()));
    deleted.clear();
    inserted.clear();
    return order + 1;
  }

  private List<String> splitParagraphs(String text) {
    List<String> paragraphs = new ArrayList<>();
    for (String line : PARAGRAPH_SPLIT.split(text)) {
      String trimmed = line.strip();
      if (!trimmed.isEmpty()) {
        paragraphs.add(trimmed);
      }
    }
    return paragraphs;
  }

  private boolean equalToken(String left, String right) {
    return normalize(left).equals(normalize(right));
  }

  private String normalize(String value) {
    return WHITESPACE.matcher(value).replaceAll(" ").strip().toLowerCase();
  }
}
