package com.jitendra.ai.sessionvault.rag;

import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.rag.chunking.ConversationChunker;
import com.jitendra.ai.sessionvault.rag.repository.RagIndexJob;
import com.jitendra.ai.sessionvault.rag.repository.RagIndexJobRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagIndexingWorker {

    private final RagIndexJobRepository jobRepository;
    private final ChatSessionRepository sessionRepository;
    private final RagIndexPersistenceService persistenceService;
    private final RagIndexingService indexingService;
    private final ConversationChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final RagProperties properties;

    public void processSession(UUID sessionId) {
        jobRepository.claim(sessionId, properties.getJobLease(), properties.getMaxAttempts()).ifPresent(this::process);
    }

    public void processNext() {
        jobRepository.failExpiredJobs(properties.getMaxAttempts());
        jobRepository.claimNext(properties.getJobLease(), properties.getMaxAttempts()).ifPresent(this::process);
    }

    private void process(RagIndexJob job) {
        String activeModel = indexingService.activeEmbeddingModelId();
        if (!activeModel.equals(job.embeddingModel())) {
            jobRepository.enqueue(job.sessionId(), activeModel);
            return;
        }

        try {
            ChatSession session = sessionRepository.findByIdWithMessages(job.sessionId())
                    .orElseThrow(() -> new IllegalStateException("Session disappeared before RAG indexing"));
            List<ConversationChunker.Chunk> chunks = chunker.chunk(session.getMessages());
            List<float[]> embeddings = embed(chunks);
            persistenceService.replaceChunks(job.sessionId(), activeModel, job.generation(), chunks, embeddings);
            log.info("Indexed RAG session {} with {} chunks", job.sessionId(), chunks.size());
        } catch (RuntimeException e) {
            recordFailure(job, e);
        }
    }

    private List<float[]> embed(List<ConversationChunker.Chunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += properties.getEmbeddingBatchSize()) {
            int end = Math.min(start + properties.getEmbeddingBatchSize(), chunks.size());
            List<String> contents = chunks.subList(start, end).stream()
                    .map(chunk -> properties.getDocumentPrefix() + chunk.content())
                    .toList();
            List<float[]> batch = embeddingModel.embed(contents);
            if (batch.size() != contents.size()) {
                throw new IllegalStateException("Embedding provider returned a different number of vectors than inputs");
            }
            for (float[] vector : batch) {
                EmbeddingVectorValidator.validate(vector, properties.getEmbeddingDimension(), "Document");
                embeddings.add(vector);
            }
        }
        return embeddings;
    }

    private void recordFailure(RagIndexJob job, RuntimeException failure) {
        String message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        try {
            if (job.attempts() >= properties.getMaxAttempts()) {
                jobRepository.markFailed(job.id(), job.generation(), message);
                log.error("RAG indexing permanently failed for session {} after {} attempts",
                        job.sessionId(), job.attempts(), failure);
            } else {
                jobRepository.markRetry(job.id(), job.generation(), retryDelay(job.attempts()), message);
                log.warn("RAG indexing failed for session {}; retry {}/{} scheduled",
                        job.sessionId(), job.attempts(), properties.getMaxAttempts(), failure);
            }
        } catch (RuntimeException persistenceFailure) {
            failure.addSuppressed(persistenceFailure);
            log.error("Unable to persist RAG indexing failure for session {}", job.sessionId(), failure);
        }
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        try {
            return properties.getRetryInitialDelay().multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            return Duration.ofDays(1);
        }
    }
}
