package com.pravoos.ai.recyclebin.internal.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.document.api.SearchActor;
import com.pravoos.ai.document.internal.repository.jpa.DocumentRepository;
import com.pravoos.ai.document.internal.search.ChunkCandidate;
import com.pravoos.ai.document.internal.search.ChunkSearchScope;
import com.pravoos.ai.document.internal.search.LexicalChunkSearchRepository;
import com.pravoos.ai.document.internal.search.VectorChunkSearchRepository;
import com.pravoos.ai.shared.config.HybridSearchProperties;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({VectorChunkSearchRepository.class, LexicalChunkSearchRepository.class})
class DocumentSearchSoftDeleteVisibilityIT {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  @TestConfiguration
  static class SearchPropertiesConfig {
    @Bean
    HybridSearchProperties hybridSearchProperties() {
      return new HybridSearchProperties(
          true,
          true,
          1.0,
          5,
          50,
          60.0,
          1.0,
          1.0,
          1.0,
          new HybridSearchProperties.Rerank(false, 0, 0, 0, true));
    }
  }

  @Autowired private DocumentRepository documentRepository;
  @Autowired private VectorChunkSearchRepository vectorChunkSearchRepository;
  @Autowired private LexicalChunkSearchRepository lexicalChunkSearchRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final UUID lawyerId = UUID.randomUUID();

  private static final int EMBEDDING_DIMENSIONS = 1536;

  private UUID insertCase(String title) {
    UUID caseId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO cases (id, lawyer_id, title, status, created_at) "
            + "VALUES (?, ?, ?, 'INTAKE', now())",
        caseId,
        lawyerId,
        title);
    return caseId;
  }

  private UUID insertDocument(UUID caseId, String title) {
    UUID documentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO documents (id, title, file_name, file_type, file_path, uploaded_by, case_id, "
            + "size_bytes, visible_to_client, document_kind, superseded, uploaded_at, status, "
            + "summary_status) "
            + "VALUES (?, ?, 'f.pdf', 'pdf', '/tmp/f.pdf', ?, ?, 10, FALSE, 'GENERAL', FALSE, "
            + "now(), 'READY', 'NONE')",
        documentId,
        title,
        lawyerId,
        caseId);
    return documentId;
  }

  private UUID insertChunk(UUID documentId, String content, float[] embedding) {
    UUID chunkId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO document_chunks (id, document_id, content, chunk_index, embedding, created_at) "
            + "VALUES (?, ?, ?, 0, CAST(? AS vector), now())",
        chunkId,
        documentId,
        content,
        toVectorLiteral(embedding));
    return chunkId;
  }

  private static float[] fixedEmbedding(float value) {
    float[] embedding = new float[EMBEDDING_DIMENSIONS];
    Arrays.fill(embedding, value);
    return embedding;
  }

  private static String toVectorLiteral(float[] embedding) {
    StringBuilder literal = new StringBuilder("[");
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) {
        literal.append(',');
      }
      literal.append(embedding[i]);
    }
    return literal.append(']').toString();
  }

  @Test
  void softDeletedDocumentDisappearsFromVectorAndLexicalSearchAndReturnsOnRestore() {
    UUID caseId = insertCase("Дело с векторным поиском");
    UUID documentId = insertDocument(caseId, "Исковое заявление о взыскании неустойки");
    float[] embedding = fixedEmbedding(0.1f);
    UUID chunkId =
        insertChunk(documentId, "Судебный акт о взыскании неустойки по договору", embedding);

    ChunkSearchScope scope = ChunkSearchScope.forCase(caseId, SearchActor.of(lawyerId, List.of()));

    assertThat(vectorChunkSearchRepository.search(embedding, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(chunkId);
    assertThat(lexicalChunkSearchRepository.search("неустойка", 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(chunkId);

    documentRepository.softDelete(documentId, LocalDateTime.now(ZoneOffset.UTC));

    assertThat(vectorChunkSearchRepository.search(embedding, 10, scope)).isEmpty();
    assertThat(lexicalChunkSearchRepository.search("неустойка", 10, scope)).isEmpty();

    documentRepository.restore(documentId);

    assertThat(vectorChunkSearchRepository.search(embedding, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(chunkId);
    assertThat(lexicalChunkSearchRepository.search("неустойка", 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(chunkId);
  }
}
