package com.jitendra.ai.sessionvault.rag.chunking;

import com.jitendra.ai.sessionvault.rag.RagProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "vault.rag.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationChunkerFactory {

    @Bean
    public ConversationChunker conversationChunker(TokenCounter tokenCounter, RagProperties properties) {
        return new ConversationChunker(
                tokenCounter,
                properties.getMaxTokens(),
                properties.getOverlapTokens(),
                properties.getDocumentPrefix());
    }
}
