package com.pravoos.ai.document.internal.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.pravoos.ai.document.api.SearchActor;
import com.pravoos.ai.shared.config.HybridSearchProperties;
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
class DocumentSearchTenantIsolationIT {

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

  private static final int EMBEDDING_DIMENSIONS = 1536;
  private static final String SHARED_QUERY = "неустойка";
  private static final String SHARED_CONTENT = "Судебный акт о взыскании неустойки по договору";

  @Autowired private VectorChunkSearchRepository vectorChunkSearchRepository;
  @Autowired private LexicalChunkSearchRepository lexicalChunkSearchRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final float[] sharedEmbedding = fixedEmbedding(0.1f);

  @Test
  void caseScopedSearchNeverReturnsChunksOfAnotherTenant() {
    Tenant own = insertTenant("Своя организация");
    Tenant foreign = insertTenant("Чужая организация");

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, own.scope()))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(own.chunkId());
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, own.scope()))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(own.chunkId());

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, foreign.scope()))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(foreign.chunkId());
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, foreign.scope()))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(foreign.chunkId());
  }

  @Test
  void documentScopedSearchNeverReturnsChunksOfAnotherTenant() {
    Tenant own = insertTenant("Своя организация");
    Tenant foreign = insertTenant("Чужая организация");

    ChunkSearchScope scope = ChunkSearchScope.forDocument(own.documentId(), own.actor());

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(own.chunkId())
        .doesNotContain(foreign.chunkId());
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(own.chunkId())
        .doesNotContain(foreign.chunkId());
  }

  @Test
  void knowledgeBaseSearchNeverReturnsCaseBoundChunksOfAnyTenant() {
    Tenant own = insertTenant("Своя организация");
    Tenant foreign = insertTenant("Чужая организация");

    ChunkSearchScope scope = ChunkSearchScope.knowledgeBase();

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .doesNotContain(own.chunkId(), foreign.chunkId());
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .doesNotContain(own.chunkId(), foreign.chunkId());
  }

  @Test
  void caseScopedSearchReturnsNothing_whenActorIsNotEntitledToTheCase() {
    Tenant own = insertTenant("Своя организация");
    Tenant foreign = insertTenant("Чужая организация");

    ChunkSearchScope stolenScope = ChunkSearchScope.forCase(foreign.caseId(), own.actor());

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, stolenScope)).isEmpty();
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, stolenScope)).isEmpty();
  }

  @Test
  void documentScopedSearchReturnsNothing_whenActorIsNotEntitledToTheCaseOfTheDocument() {
    Tenant own = insertTenant("Своя организация");
    Tenant foreign = insertTenant("Чужая организация");

    ChunkSearchScope stolenScope = ChunkSearchScope.forDocument(foreign.documentId(), own.actor());

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, stolenScope)).isEmpty();
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, stolenScope)).isEmpty();
  }

  @Test
  void caseScopedSearchReturnsChunks_whenActorSharesTheOrganizationOfTheCase() {
    UUID orgId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID caseId = insertCase(ownerId, orgId, "Дело организации");
    UUID documentId = insertDocument(caseId, ownerId, "Дело организации", "GENERAL");
    UUID chunkId = insertChunk(documentId, SHARED_CONTENT, sharedEmbedding);

    SearchActor colleague = SearchActor.of(UUID.randomUUID(), List.of(orgId));
    ChunkSearchScope scope = ChunkSearchScope.forCase(caseId, colleague);

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(chunkId);
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, scope))
        .extracting(ChunkCandidate::chunkId)
        .containsExactly(chunkId);

    SearchActor outsider = SearchActor.of(UUID.randomUUID(), List.of(UUID.randomUUID()));
    assertThat(
            vectorChunkSearchRepository.search(
                sharedEmbedding, 10, ChunkSearchScope.forCase(caseId, outsider)))
        .isEmpty();
  }

  @Test
  void caseScopedSearchReturnsNothing_whenTheCaseItselfIsSoftDeleted() {
    Tenant own = insertTenant("Своя организация");
    jdbcTemplate.update("UPDATE cases SET deleted_at = now() WHERE id = ?", own.caseId());

    assertThat(vectorChunkSearchRepository.search(sharedEmbedding, 10, own.scope())).isEmpty();
    assertThat(lexicalChunkSearchRepository.search(SHARED_QUERY, 10, own.scope())).isEmpty();
  }

  @Test
  void chatAttachmentsOfOneTenantStayOutOfTheSharedKnowledgeBase() {
    UUID lawyerId = UUID.randomUUID();
    UUID attachmentId = insertDocument(null, lawyerId, "Чужая переписка", "CHAT_ATTACHMENT");
    UUID attachmentChunkId = insertChunk(attachmentId, SHARED_CONTENT, sharedEmbedding);

    assertThat(
            vectorChunkSearchRepository.search(
                sharedEmbedding, 10, ChunkSearchScope.knowledgeBase()))
        .extracting(ChunkCandidate::chunkId)
        .doesNotContain(attachmentChunkId);
    assertThat(
            vectorChunkSearchRepository.search(
                sharedEmbedding,
                10,
                ChunkSearchScope.forCase(
                    insertCase(lawyerId, null, "Дело"), SearchActor.of(lawyerId, List.of()))))
        .extracting(ChunkCandidate::chunkId)
        .doesNotContain(attachmentChunkId);
  }

  private Tenant insertTenant(String title) {
    UUID lawyerId = UUID.randomUUID();
    UUID caseId = insertCase(lawyerId, null, title);
    UUID documentId = insertDocument(caseId, lawyerId, title, "GENERAL");
    UUID chunkId = insertChunk(documentId, SHARED_CONTENT, sharedEmbedding);
    return new Tenant(lawyerId, caseId, documentId, chunkId);
  }

  private UUID insertCase(UUID lawyerId, UUID orgId, String title) {
    UUID caseId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO cases (id, lawyer_id, org_id, title, status, created_at) "
            + "VALUES (?, ?, ?, ?, 'INTAKE', now())",
        caseId,
        lawyerId,
        orgId,
        title);
    return caseId;
  }

  private UUID insertDocument(UUID caseId, UUID uploadedBy, String title, String documentKind) {
    UUID documentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO documents (id, title, file_name, file_type, file_path, uploaded_by, case_id, "
            + "size_bytes, visible_to_client, document_kind, superseded, uploaded_at, status, "
            + "summary_status) "
            + "VALUES (?, ?, 'f.pdf', 'pdf', '/tmp/f.pdf', ?, ?, 10, FALSE, ?, FALSE, "
            + "now(), 'READY', 'NONE')",
        documentId,
        title,
        uploadedBy,
        caseId,
        documentKind);
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
    for (int index = 0; index < embedding.length; index++) {
      if (index > 0) {
        literal.append(',');
      }
      literal.append(embedding[index]);
    }
    return literal.append(']').toString();
  }

  private record Tenant(UUID lawyerId, UUID caseId, UUID documentId, UUID chunkId) {

    private SearchActor actor() {
      return SearchActor.of(lawyerId, List.of());
    }

    private ChunkSearchScope scope() {
      return ChunkSearchScope.forCase(caseId, actor());
    }
  }
}
