package com.jitendra.ai.sessionvault.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.rag.dto.RagSearchResponse;
import com.jitendra.ai.sessionvault.rag.repository.HybridCandidate;
import com.jitendra.ai.sessionvault.rag.repository.HybridSearchRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
class HybridSearchRepositoryIntegrationTest {

    private static final String EMBEDDING_MODEL = "openai:text-embedding-3-small";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    private static HybridSearchRepository searchRepository;

    @BeforeAll
    static void migrateAndInsertTestData() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .placeholders(java.util.Map.of("embeddingDimension", "3"))
                .load()
                .migrate();
        searchRepository = new HybridSearchRepository(JdbcClient.create(dataSource));

        JdbcClient jdbc = JdbcClient.create(dataSource);
        insertSession(jdbc, "dense only", "No matching keyword", "[0.99,0.01,0.0]");
        insertSession(jdbc, "sparse only", "orion keyword match", "[0.0,1.0,0.0]");
        insertSession(jdbc, "hybrid match", "orion semantic match", "[0.9,0.4,0.0]");
    }

    @Test
    void combinesDenseAndSparseRanksUsingWeightedReciprocalRankFusion() {
        List<HybridCandidate> results = searchRepository.search(
                new float[]{1.0f, 0.0f, 0.0f},
                "orion",
                EMBEDDING_MODEL,
                null,
                null,
                "[]",
                "{}",
                10,
                0.0,
                0.5,
                60,
                0.0,
                Duration.ofDays(90));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).sessionTitle()).isEqualTo("hybrid match");
    }

    @Test
    void semanticRecallMcpToolReturnsGroundedHybridResults() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("orion")).thenReturn(new float[]{1.0f, 0.0f, 0.0f});
        RagIndexingService indexingService = mock(RagIndexingService.class);
        when(indexingService.activeEmbeddingModelId()).thenReturn(EMBEDDING_MODEL);
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimension(3);

        RagSearchService service = new RagSearchService(
                embeddingModel,
                searchRepository,
                new ObjectMapper(),
                properties,
                indexingService);
        RagMcpTools tools = new RagMcpTools(service, mock(RagRelatedSessionService.class));

        RagSearchResponse response = tools.semanticRecall("orion", 3);

        assertThat(response.results()).hasSize(3);
        assertThat(response.results().get(0).sessionTitle()).isEqualTo("hybrid match");
        assertThat(response.results().get(0).content()).contains("orion");
    }

    private static void insertSession(JdbcClient jdbc, String title, String content, String vector) {
        java.util.UUID sessionId = jdbc.sql("""
                INSERT INTO chat_sessions (title) VALUES (:title) RETURNING id
                """)
                .param("title", title)
                .query(java.util.UUID.class)
                .single();
        jdbc.sql("""
                INSERT INTO rag_index_jobs (session_id, embedding_model, status)
                VALUES (:sessionId, :model, 'INDEXED')
                """)
                .param("sessionId", sessionId)
                .param("model", EMBEDDING_MODEL)
                .update();
        jdbc.sql("""
                INSERT INTO session_chunks
                    (session_id, chunk_index, role_sequence, content, token_count, embedding,
                     embedding_model, content_hash)
                VALUES (:sessionId, 0, 'USER>MODEL', :content, 4, CAST(:vector AS vector), :model, :hash)
                """)
                .param("sessionId", sessionId)
                .param("content", content)
                .param("vector", vector)
                .param("model", EMBEDDING_MODEL)
                .param("hash", Integer.toHexString(content.hashCode()))
                .update();
    }
}
