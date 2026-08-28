package com.pravoos.llm.openai;

import com.pravoos.llm.domain.LlmToolCall;
import com.pravoos.llm.exception.LlmException;
import com.pravoos.llm.openai.dto.OpenAiStreamChunk;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ToolCallAccumulator {

  private static final String EMPTY_ARGUMENTS = "{}";

  private final Map<Integer, PartialToolCall> partialsByIndex = new TreeMap<>();
  private final int maxArgumentChars;

  public ToolCallAccumulator(int maxArgumentChars) {
    this.maxArgumentChars = maxArgumentChars;
  }

  public void accept(List<OpenAiStreamChunk.ToolCallDelta> deltas) {
    if (deltas == null || deltas.isEmpty()) {
      return;
    }
    for (OpenAiStreamChunk.ToolCallDelta delta : deltas) {
      if (delta != null) {
        merge(delta);
      }
    }
  }

  public boolean isEmpty() {
    return partialsByIndex.isEmpty();
  }

  public List<LlmToolCall> build() {
    return partialsByIndex.values().stream()
        .filter(PartialToolCall::isComplete)
        .map(PartialToolCall::toToolCall)
        .toList();
  }

  private void merge(OpenAiStreamChunk.ToolCallDelta delta) {
    int index = delta.index() == null ? 0 : delta.index();
    PartialToolCall partial =
        partialsByIndex.computeIfAbsent(index, key -> new PartialToolCall(maxArgumentChars));
    partial.mergeId(delta.id());
    if (delta.function() != null) {
      partial.appendName(delta.function().name());
      partial.appendArguments(delta.function().arguments());
    }
  }

  private static final class PartialToolCall {

    private final int maxArgumentChars;
    private final StringBuilder name = new StringBuilder();
    private final StringBuilder arguments = new StringBuilder();
    private String id;

    private PartialToolCall(int maxArgumentChars) {
      this.maxArgumentChars = maxArgumentChars;
    }

    private void mergeId(String fragment) {
      if (id == null && fragment != null && !fragment.isEmpty()) {
        id = fragment;
      }
    }

    private void appendName(String fragment) {
      if (fragment != null && !fragment.isEmpty()) {
        name.append(fragment);
      }
    }

    private void appendArguments(String fragment) {
      if (fragment == null || fragment.isEmpty()) {
        return;
      }
      if (arguments.length() + fragment.length() > maxArgumentChars) {
        throw new LlmException(
            "Tool call arguments exceeded "
                + maxArgumentChars
                + " characters for tool '"
                + name
                + "', aborting stream");
      }
      arguments.append(fragment);
    }

    private boolean isComplete() {
      return id != null && !id.isEmpty() && !name.isEmpty();
    }

    private LlmToolCall toToolCall() {
      return new LlmToolCall(
          id, name.toString(), arguments.isEmpty() ? EMPTY_ARGUMENTS : arguments.toString());
    }
  }
}
