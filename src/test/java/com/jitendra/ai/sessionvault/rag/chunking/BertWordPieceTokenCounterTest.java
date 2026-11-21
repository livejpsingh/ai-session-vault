package com.jitendra.ai.sessionvault.rag.chunking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BertWordPieceTokenCounterTest {

    @TempDir
    Path tempDir;

    @Test
    void countsWordPiecesAndSpecialTokens() throws IOException {
        Path vocabulary = tempDir.resolve("vocab.txt");
        Files.write(vocabulary, java.util.List.of(
                "[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "world", "##s", "!"));

        BertWordPieceTokenCounter counter = new BertWordPieceTokenCounter(vocabulary);

        assertThat(counter.count("Hello worlds!")).isEqualTo(6);
    }
}
