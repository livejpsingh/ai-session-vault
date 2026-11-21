package com.jitendra.ai.sessionvault.rag;

import com.jitendra.ai.sessionvault.rag.chunking.BertWordPieceTokenCounter;
import com.jitendra.ai.sessionvault.rag.chunking.JTokkitTokenCounter;
import com.jitendra.ai.sessionvault.rag.chunking.TokenCounter;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(RagProperties.class)
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagConfiguration {

    @Bean("ragTaskExecutor")
    public TaskExecutor ragTaskExecutor(RagProperties properties) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("rag-index-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(properties.getMaxConcurrentJobs());
        return executor;
    }

    @Bean
    public TokenCounter tokenCounter(RagProperties properties) {
        return switch (properties.getTokenizer().toLowerCase()) {
            case "bert-wordpiece" -> new BertWordPieceTokenCounter(Path.of(properties.getTokenizerVocabPath()));
            case "cl100k_base" -> new JTokkitTokenCounter(EncodingType.CL100K_BASE);
            default -> throw new IllegalStateException(
                    "Unsupported RAG tokenizer: " + properties.getTokenizer());
        };
    }
}
