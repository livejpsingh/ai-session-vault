package com.jitendra.ai.sessionvault.rag.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RagIndexJobRepository {

    private static final String CLAIM_COLUMNS = """
            RETURNING j.id, j.session_id, j.embedding_model, j.status, j.attempts, j.generation
            """;

    private final JdbcClient jdbcClient;

    public int enqueue(UUID sessionId, String embeddingModel) {
        return jdbcClient.sql("""
                INSERT INTO rag_index_jobs (session_id, embedding_model)
                VALUES (:sessionId, :embeddingModel)
                ON CONFLICT (session_id) DO UPDATE
                SET embedding_model = EXCLUDED.embedding_model,
                    status = 'PENDING',
                    attempts = 0,
                    generation = rag_index_jobs.generation + 1,
                    next_attempt_at = CURRENT_TIMESTAMP,
                    locked_until = NULL,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param("sessionId", sessionId)
                .param("embeddingModel", embeddingModel)
                .update();
    }

    public int enqueueAll(String embeddingModel) {
        return jdbcClient.sql("""
                INSERT INTO rag_index_jobs (session_id, embedding_model)
                SELECT id, :embeddingModel FROM chat_sessions
                ON CONFLICT (session_id) DO UPDATE
                SET embedding_model = EXCLUDED.embedding_model,
                    status = 'PENDING',
                    attempts = 0,
                    generation = rag_index_jobs.generation + 1,
                    next_attempt_at = CURRENT_TIMESTAMP,
                    locked_until = NULL,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                """)
                .param("embeddingModel", embeddingModel)
                .update();
    }

    public Optional<RagIndexJob> claim(UUID sessionId, Duration lease, int maxAttempts) {
        return jdbcClient.sql("""
                WITH candidate AS (
                    SELECT id
                    FROM rag_index_jobs
                    WHERE session_id = :sessionId
                      AND attempts < :maxAttempts
                      AND ((status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                           OR (status = 'PROCESSING' AND locked_until <= CURRENT_TIMESTAMP))
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE rag_index_jobs j
                SET status = 'PROCESSING',
                    attempts = j.attempts + 1,
                    locked_until = CURRENT_TIMESTAMP + make_interval(secs => :leaseSeconds),
                    updated_at = CURRENT_TIMESTAMP
                FROM candidate
                WHERE j.id = candidate.id
                """ + CLAIM_COLUMNS)
                .param("sessionId", sessionId)
                .param("leaseSeconds", lease.toSeconds())
                .param("maxAttempts", maxAttempts)
                .query((rs, rowNum) -> new RagIndexJob(
                        rs.getObject("id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getString("embedding_model"),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getLong("generation")))
                .optional();
    }

    public Optional<RagIndexJob> claimNext(Duration lease, int maxAttempts) {
        return jdbcClient.sql("""
                WITH candidate AS (
                    SELECT id
                    FROM rag_index_jobs
                    WHERE attempts < :maxAttempts
                      AND ((status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                           OR (status = 'PROCESSING' AND locked_until <= CURRENT_TIMESTAMP))
                    ORDER BY next_attempt_at, created_at
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE rag_index_jobs j
                SET status = 'PROCESSING',
                    attempts = j.attempts + 1,
                    locked_until = CURRENT_TIMESTAMP + make_interval(secs => :leaseSeconds),
                    updated_at = CURRENT_TIMESTAMP
                FROM candidate
                WHERE j.id = candidate.id
                """ + CLAIM_COLUMNS)
                .param("leaseSeconds", lease.toSeconds())
                .param("maxAttempts", maxAttempts)
                .query((rs, rowNum) -> new RagIndexJob(
                        rs.getObject("id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getString("embedding_model"),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getLong("generation")))
                .optional();
    }

    public int failExpiredJobs(int maxAttempts) {
        return jdbcClient.sql("""
                UPDATE rag_index_jobs
                SET status = 'FAILED',
                    locked_until = NULL,
                    last_error = 'Worker lease expired after maximum attempts',
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = 'PROCESSING'
                  AND locked_until <= CURRENT_TIMESTAMP
                  AND attempts >= :maxAttempts
                """)
                .param("maxAttempts", maxAttempts)
                .update();
    }

    public int markRetry(UUID id, long generation, Duration delay, String error) {
        return jdbcClient.sql("""
                UPDATE rag_index_jobs
                SET status = 'PENDING',
                    next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => :delaySeconds),
                    locked_until = NULL,
                    last_error = :error,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND generation = :generation AND status = 'PROCESSING'
                """)
                .param("delaySeconds", delay.toSeconds())
                .param("error", truncate(error))
                .param("id", id)
                .param("generation", generation)
                .update();
    }

    public int markFailed(UUID id, long generation, String error) {
        return jdbcClient.sql("""
                UPDATE rag_index_jobs
                SET status = 'FAILED',
                    locked_until = NULL,
                    last_error = :error,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND generation = :generation AND status = 'PROCESSING'
                """)
                .param("error", truncate(error))
                .param("id", id)
                .param("generation", generation)
                .update();
    }

    public int markIndexed(UUID sessionId, String embeddingModel, long generation) {
        return jdbcClient.sql("""
                UPDATE rag_index_jobs
                SET status = 'INDEXED',
                    locked_until = NULL,
                    last_error = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE session_id = :sessionId AND embedding_model = :embeddingModel
                  AND generation = :generation AND status = 'PROCESSING'
                """)
                .param("sessionId", sessionId)
                .param("embeddingModel", embeddingModel)
                .param("generation", generation)
                .update();
    }

    public Optional<RagIndexJobStatus> findStatus(UUID sessionId) {
        return jdbcClient.sql("""
                SELECT status, attempts, next_attempt_at, updated_at
                FROM rag_index_jobs WHERE session_id = :sessionId
                """)
                .param("sessionId", sessionId)
                .query((rs, rowNum) -> new RagIndexJobStatus(
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getObject("next_attempt_at", java.time.OffsetDateTime.class),
                        rs.getObject("updated_at", java.time.OffsetDateTime.class)))
                .optional();
    }

    private String truncate(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown indexing failure";
        }
        return error.length() > 2000 ? error.substring(0, 2000) : error;
    }
}
