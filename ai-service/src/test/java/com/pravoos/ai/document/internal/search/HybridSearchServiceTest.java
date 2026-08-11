package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pravoos.ai.document.internal.service.EmbeddingService;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HybridSearchServiceTest {

  private static final String QUERY = "срок исковой давности";
  private static final int TOP_K = 2;

  @Mock private EmbeddingService embeddingService;
  @Mock private VectorChunkSearchRepository vectorSearchRepository;
  @Mock private LexicalChunkSearchRepository lexicalSearchRepository;

  private final UUID vectorOnly = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private final UUID shared = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private final UUID lexicalOnly = UUID.fromString("00000000-0000-0000-0000-000000000003");

  @BeforeEach
  void stubEmbedding() {
    when(embeddingService.embed(anyString())).thenReturn(new float[] {0.1f, 0.2f});
  }

  @Test
  void mergesBothSourcesAndReturnsTopK() {
    when(vectorSearchRepository.search(any(), anyInt(), any()))
        .thenReturn(List.of(candidate(vectorOnly), candidate(shared)));
    when(lexicalSearchRepository.search(anyString(), anyInt(), any()))
        .thenReturn(List.of(candidate(shared), candidate(lexicalOnly)));

    List<ChunkCandidate> results =
        service(properties(true, true))
            .search(QUERY, TOP_K, ChunkSearchScope.knowledgeBase())
            .candidates();

    assertThat(results).extracting(ChunkCandidate::chunkId).containsExactly(shared, vectorOnly);
  }

  @Test
  void surfacesLexicalOnlyMatchThatVectorSearchMissed() {
    when(vectorSearchRepository.search(any(), anyInt(), any())).thenReturn(List.of());
    when(lexicalSearchRepository.search(anyString(), anyInt(), any()))
        .thenReturn(List.of(candidate(lexicalOnly)));

    List<ChunkCandidate> results =
        service(properties(true, true))
            .search(QUERY, TOP_K, ChunkSearchScope.knowledgeBase())
            .candidates();

    assertThat(results).extracting(ChunkCandidate::chunkId).containsExactly(lexicalOnly);
  }

  @Test
  void skipsLexicalSourceWhenDisabled() {
    when(vectorSearchRepository.search(any(), anyInt(), any()))
        .thenReturn(List.of(candidate(vectorOnly)));

    List<ChunkCandidate> results =
        service(properties(false, true))
            .search(QUERY, TOP_K, ChunkSearchScope.knowledgeBase())
            .candidates();

    assertThat(results).extracting(ChunkCandidate::chunkId).containsExactly(vectorOnly);
    verify(lexicalSearchRepository, never()).search(anyString(), anyInt(), any());
  }

  @Test
  void degradesToVectorOnlyWhenLexicalSearchFails() {
    when(vectorSearchRepository.search(any(), anyInt(), any()))
        .thenReturn(List.of(candidate(vectorOnly)));
    when(lexicalSearchRepository.search(anyString(), anyInt(), any()))
        .thenThrow(
            new InvalidDataAccessResourceUsageException("column content_tsv does not exist"));

    List<ChunkCandidate> results =
        service(properties(true, true))
            .search(QUERY, TOP_K, ChunkSearchScope.knowledgeBase())
            .candidates();

    assertThat(results).extracting(ChunkCandidate::chunkId).containsExactly(vectorOnly);
  }

  @Test
  void passesCaseScopeToBothSources() {
    UUID caseId = UUID.randomUUID();
    ChunkSearchScope expectedScope = ChunkSearchScope.forCase(caseId);
    when(vectorSearchRepository.search(any(), anyInt(), any()))
        .thenReturn(List.of(candidate(vectorOnly)));
    when(lexicalSearchRepository.search(anyString(), anyInt(), any())).thenReturn(List.of());

    service(properties(true, true)).search(QUERY, TOP_K, expectedScope);

    verify(vectorSearchRepository).search(any(), anyInt(), eq(expectedScope));
    verify(lexicalSearchRepository).search(eq(QUERY), anyInt(), eq(expectedScope));
  }

  @Test
  void requestsMoreCandidatesThanTopKForFusion() {
    when(vectorSearchRepository.search(any(), anyInt(), any())).thenReturn(List.of());
    when(lexicalSearchRepository.search(anyString(), anyInt(), any())).thenReturn(List.of());

    service(properties(true, true)).search(QUERY, TOP_K, ChunkSearchScope.knowledgeBase());

    verify(vectorSearchRepository).search(any(), eq(TOP_K * 6), any());
    verify(lexicalSearchRepository).search(eq(QUERY), eq(TOP_K * 6), any());
  }

  @Test
  void returnsEmptyForBlankQueryWithoutTouchingSources() {
    List<ChunkCandidate> results =
        service(properties(true, true))
            .search("   ", TOP_K, ChunkSearchScope.knowledgeBase())
            .candidates();

    assertThat(results).isEmpty();
    verify(embeddingService, never()).embed(anyString());
    verify(vectorSearchRepository, never()).search(any(), anyInt(), any());
  }

  private HybridSearchService service(HybridSearchProperties properties) {
    return new HybridSearchService(
        embeddingService,
        vectorSearchRepository,
        lexicalSearchRepository,
        new PassThroughReranker(),
        properties);
  }

  private HybridSearchProperties properties(boolean lexicalEnabled, boolean rerankEnabled) {
    return new HybridSearchProperties(
        lexicalEnabled,
        0.85,
        6,
        60,
        60.0,
        1.0,
        0.7,
        0.005,
        new HybridSearchProperties.Rerank(rerankEnabled, 20, 700, 400, true));
  }

  private ChunkCandidate candidate(UUID id) {
    return new ChunkCandidate(id, 0, "content-" + id, "doc", false, null, null, null, 0.5);
  }
}
