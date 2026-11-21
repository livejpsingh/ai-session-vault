package com.jitendra.ai.sessionvault.rag.chunking;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.MessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationChunkerTest {

    private final TokenCounter wordCounter = text ->
            text.isBlank() ? 0 : text.trim().split("\\s+").length;

    @Test
    void keepsQuestionAndAnswerTogetherAndAddsBoundaryAlignedOverlap() {
        ConversationChunker chunker = new ConversationChunker(wordCounter, 11, 4);
        List<ChatMessage> messages = List.of(
                message(1, MessageRole.USER, "Need help"),
                message(2, MessageRole.MODEL, "Use this fix"),
                message(3, MessageRole.USER, "Another question with several more words"));

        List<ConversationChunker.Chunk> chunks = chunker.chunk(messages);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).contains("[USER]", "[MODEL]");
        assertThat(chunks.get(1).content()).contains("[USER]", "Use this fix");
        assertThat(chunks.get(0).roleSequence()).isEqualTo("USER>MODEL");
    }

    @Test
    void neverSplitsInsideAFencedCodeBlockEvenWhenItExceedsTarget() {
        ConversationChunker chunker = new ConversationChunker(wordCounter, 8, 2);
        String code = """
                Before the code.

                ```java
                public class Example {
                    public void run() { System.out.println("a long code block"); }
                }
                ```

                After the code.
                """;

        List<ConversationChunker.Chunk> chunks = chunker.chunk(List.of(message(1, MessageRole.USER, code)));

        ConversationChunker.Chunk codeChunk = chunks.stream()
                .filter(chunk -> chunk.content().contains("public class Example"))
                .findFirst()
                .orElseThrow();
        assertThat(codeChunk.content()).contains("```java", "```");
        assertThat(codeChunk.oversized()).isTrue();
    }

    @Test
    void includesEmbeddingPrefixInChunkTokenBudgetAndCount() {
        ConversationChunker chunker = new ConversationChunker(wordCounter, 3, 0, "search_document: ");

        List<ConversationChunker.Chunk> chunks = chunker.chunk(
                List.of(message(1, MessageRole.USER, "first\n\nsecond")));

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(3));
    }

    private ChatMessage message(int sequence, MessageRole role, String content) {
        return ChatMessage.builder()
                .id(UUID.randomUUID())
                .sequenceNumber(sequence)
                .role(role)
                .content(content)
                .build();
    }
}
