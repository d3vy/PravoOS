package com.pravoos.ai.core.internal.service;

import com.pravoos.ai.core.internal.dto.DiffChange;
import com.pravoos.ai.shared.model.enums.DiffChangeType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TextDiffService {

  private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\r?\\n");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  public List<DiffChange> diff(String baseText, String revisedText) {
    List<String> base = splitParagraphs(baseText);
    List<String> revised = splitParagraphs(revisedText);
    List<Op> ops = align(base, revised);
    return groupChanges(ops);
  }

  private List<String> splitParagraphs(String text) {
    List<String> paragraphs = new ArrayList<>();
    if (text == null) {
      return paragraphs;
    }
    for (String line : PARAGRAPH_SPLIT.split(text)) {
      String trimmed = line.strip();
      if (!trimmed.isEmpty()) {
        paragraphs.add(trimmed);
      }
    }
    return paragraphs;
  }

  private List<Op> align(List<String> base, List<String> revised) {
    int n = base.size();
    int m = revised.size();
    int[][] lcs = new int[n + 1][m + 1];
    for (int i = n - 1; i >= 0; i--) {
      for (int j = m - 1; j >= 0; j--) {
        if (equalParagraph(base.get(i), revised.get(j))) {
          lcs[i][j] = lcs[i + 1][j + 1] + 1;
        } else {
          lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
        }
      }
    }

    List<Op> ops = new ArrayList<>();
    int i = 0;
    int j = 0;
    while (i < n && j < m) {
      if (equalParagraph(base.get(i), revised.get(j))) {
        ops.add(new Op(OpType.EQUAL, base.get(i)));
        i++;
        j++;
      } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
        ops.add(new Op(OpType.DELETE, base.get(i)));
        i++;
      } else {
        ops.add(new Op(OpType.INSERT, revised.get(j)));
        j++;
      }
    }
    while (i < n) {
      ops.add(new Op(OpType.DELETE, base.get(i++)));
    }
    while (j < m) {
      ops.add(new Op(OpType.INSERT, revised.get(j++)));
    }
    return ops;
  }

  private List<DiffChange> groupChanges(List<Op> ops) {
    List<DiffChange> changes = new ArrayList<>();
    List<String> deleted = new ArrayList<>();
    List<String> inserted = new ArrayList<>();
    int order = 0;
    for (Op op : ops) {
      switch (op.type()) {
        case DELETE -> deleted.add(op.text());
        case INSERT -> inserted.add(op.text());
        case EQUAL -> order = flush(changes, deleted, inserted, order);
      }
    }
    flush(changes, deleted, inserted, order);
    return changes;
  }

  private int flush(
      List<DiffChange> changes, List<String> deleted, List<String> inserted, int order) {
    if (deleted.isEmpty() && inserted.isEmpty()) {
      return order;
    }
    DiffChangeType type;
    if (deleted.isEmpty()) {
      type = DiffChangeType.ADDED;
    } else if (inserted.isEmpty()) {
      type = DiffChangeType.REMOVED;
    } else {
      type = DiffChangeType.MODIFIED;
    }
    changes.add(
        new DiffChange(
            order, type, String.join("\n", deleted), String.join("\n", inserted), null, null));
    deleted.clear();
    inserted.clear();
    return order + 1;
  }

  private boolean equalParagraph(String left, String right) {
    return normalize(left).equals(normalize(right));
  }

  private String normalize(String value) {
    return WHITESPACE.matcher(value).replaceAll(" ").strip().toLowerCase();
  }

  private enum OpType {
    EQUAL,
    DELETE,
    INSERT
  }

  private record Op(OpType type, String text) {}
}
