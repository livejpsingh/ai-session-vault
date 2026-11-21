package com.jitendra.ai.sessionvault.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitendra.ai.sessionvault.rag.chunking.ConversationChunker;
import com.jitendra.ai.sessionvault.rag.repository.RagIndexJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagIndexPersistenceService {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RagIndexJobRepository jobRepository;

    @Transactional
    public void replaceChunks(
            UUID sessionId,
            String embeddingModel,
            long generation,
            List<ConversationChunker.Chunk> chunks,
            List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("The number of chunks and embeddings must match");
        }

        jdbcClient.sql("DELETE FROM session_chunks WHERE session_id = :sessionId")
                .param("sessionId", sessionId)
                .update();

        for (int i = 0; i < chunks.size(); i++) {
            ConversationChunker.Chunk chunk = chunks.get(i);
            Map<String, Object> metadata = Map.of(
                    "messageStart", chunk.startSequence(),
                    "messageEnd", chunk.endSequence(),
                    "oversized", chunk.oversized());
            jdbcClient.sql("""
                    INSERT INTO session_chunks
                        (session_id, chunk_index, role_sequence, content, token_count, metadata,
                         embedding, embedding_model, content_hash)
                    VALUES
                        (:sessionId, :chunkIndex, :roleSequence, :content, :tokenCount,
                         CAST(:metadata AS jsonb), CAST(:embedding AS vector), :embeddingModel, :contentHash)
                    """)
                    .param("sessionId", sessionId)
                    .param("chunkIndex", chunk.index())
                    .param("roleSequence", chunk.roleSequence())
                    .param("content", chunk.content())
                    .param("tokenCount", chunk.tokenCount())
                    .param("metadata", json(metadata))
                    .param("embedding", vectorLiteral(embeddings.get(i)))
                    .param("embeddingModel", embeddingModel)
                    .param("contentHash", chunk.contentHash())
                    .update();
        }

        if (jobRepository.markIndexed(sessionId, embeddingModel, generation) != 1) {
            throw new IllegalStateException("RAG index job disappeared while persisting chunks for " + sessionId);
        }
    }

    private String json(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize chunk metadata", e);
        }
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
}
