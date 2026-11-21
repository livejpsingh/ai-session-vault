package com.jitendra.ai.sessionvault.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagIndexingEventListener {

    private final RagIndexingWorker indexingWorker;

    @Async("ragTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIndexRequested(RagIndexRequestedEvent event) {
        indexingWorker.processSession(event.sessionId());
    }

    @Async("ragTaskExecutor")
    @Scheduled(fixedDelayString = "${vault.rag.poll-interval:5s}")
    public void pollPendingJobs() {
        indexingWorker.processNext();
    }
}
