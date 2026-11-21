package com.jitendra.ai.sessionvault.rag.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HybridSearchRepository {

    private static final String SEARCH_SQL = """
            WITH filtered AS NOT MATERIALIZED (
                SELECT c.id, c.session_id, c.content, c.role_sequence, c.embedding,
                       c.search_vector, s.external_session_id, s.title, s.project_name, s.created_at
                FROM session_chunks c
                JOIN rag_index_jobs j
                  ON j.session_id = c.session_id
                 AND j.status = 'INDEXED'
                 AND j.embedding_model = c.embedding_model
                JOIN chat_sessions s ON s.id = c.session_id
                WHERE c.embedding_model = :embeddingModel
                  AND j.embedding_model = :embeddingModel
                  AND (CAST(:project AS text) IS NULL OR s.project_name = CAST(:project AS text))
                  AND (CAST(:agent AS text) IS NULL OR s.metadata ->> 'agent' = CAST(:agent AS text))
                  AND (CAST(:metadataFilter AS jsonb) = '{}'::jsonb
                       OR c.metadata @> CAST(:metadataFilter AS jsonb))
                  AND (CAST(:tagsJson AS jsonb) = '[]'::jsonb OR EXISTS (
                      SELECT 1
                      FROM session_tags st
                      JOIN tags t ON t.id = st.tag_id
                      WHERE st.session_id = s.id
                        AND lower(t.name) IN (
                            SELECT lower(value)
                            FROM jsonb_array_elements_text(CAST(:tagsJson AS jsonb)) AS tag_values(value)
                        )
                  ))
            ),
            dense AS (
                SELECT id, row_number() OVER (ORDER BY embedding <=> CAST(:queryVector AS vector), id) AS dense_rank
                FROM filtered
                ORDER BY embedding <=> CAST(:queryVector AS vector), id
                LIMIT :candidateLimit
            ),
            sparse AS (
                SELECT id, keyword_rank,
                       row_number() OVER (ORDER BY keyword_rank DESC, id) AS sparse_rank
                FROM (
                    SELECT id, ts_rank_cd(search_vector, plainto_tsquery('english', :queryText)) AS keyword_rank
                    FROM filtered
                    WHERE search_vector @@ plainto_tsquery('english', :queryText)
                ) lexical
                ORDER BY keyword_rank DESC, id
                LIMIT :candidateLimit
            ),
            ranked AS (
                SELECT f.*, d.dense_rank, sp.sparse_rank, COALESCE(sp.keyword_rank, 0.0) AS keyword_rank
                FROM filtered f
                LEFT JOIN dense d ON d.id = f.id
                LEFT JOIN sparse sp ON sp.id = f.id
                WHERE d.id IS NOT NULL OR sp.id IS NOT NULL
            )
            SELECT r.id AS chunk_id,
                   r.session_id,
                   r.external_session_id,
                   r.title AS session_title,
                   r.project_name,
                   r.content,
                   r.role_sequence,
                   (1.0 - (r.embedding <=> CAST(:queryVector AS vector))) AS semantic_similarity,
                   r.keyword_rank,
                   (COALESCE(CAST(:hybridWeight AS double precision)
                             / (CAST(:rrfK AS double precision) + r.dense_rank), 0.0)
                    + COALESCE((1.0 - CAST(:hybridWeight AS double precision))
                               / (CAST(:rrfK AS double precision) + r.sparse_rank), 0.0))
                   * (1.0 + CAST(:recencyWeight AS double precision)
                      * EXP(-LN(2.0)
                            * GREATEST(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - r.created_at)), 0.0)
                            / CAST(:halfLifeSeconds AS double precision))) AS score,
                   r.created_at AS session_created_at,
                   r.embedding::text AS embedding
            FROM ranked r
            WHERE (1.0 - (r.embedding <=> CAST(:queryVector AS vector))) >= :minScore
            ORDER BY score DESC, r.id
            LIMIT :candidateLimit
            """;

    private final JdbcClient jdbcClient;

    public List<HybridCandidate> search(
            float[] queryVector,
            String queryText,
            String embeddingModel,
            String project,
            String agent,
            String tagsJson,
            String metadataFilterJson,
            int candidateLimit,
            double minScore,
            double hybridWeight,
            int rrfK,
            double recencyWeight,
            Duration recencyHalfLife) {

        return jdbcClient.sql(SEARCH_SQL)
                .param("queryVector", vectorLiteral(queryVector))
                .param("queryText", queryText)
                .param("embeddingModel", embeddingModel)
                .param("project", project)
                .param("agent", agent)
                .param("tagsJson", tagsJson)
                .param("metadataFilter", metadataFilterJson)
                .param("candidateLimit", candidateLimit)
                .param("minScore", minScore)
                .param("hybridWeight", hybridWeight)
                .param("rrfK", rrfK)
                .param("recencyWeight", recencyWeight)
                .param("halfLifeSeconds", Math.max(1, recencyHalfLife.toSeconds()))
                .query((rs, rowNum) -> new HybridCandidate(
                        rs.getObject("chunk_id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getString("external_session_id"),
                        rs.getString("session_title"),
                        rs.getString("project_name"),
                        rs.getString("content"),
                        rs.getString("role_sequence"),
                        rs.getDouble("score"),
                        rs.getDouble("semantic_similarity"),
                        rs.getDouble("keyword_rank"),
                        rs.getObject("session_created_at", java.time.OffsetDateTime.class),
                        parseVector(rs.getString("embedding"))))
                .list();
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                value.append(',');
            }
            value.append(Float.toString(vector[i]));
        }
        return value.append(']').toString();
    }

    private float[] parseVector(String literal) {
        if (literal == null || literal.length() < 2) {
            throw new IllegalStateException("PostgreSQL returned an invalid pgvector value");
        }
        String[] parts = literal.substring(1, literal.length() - 1).split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }
}
