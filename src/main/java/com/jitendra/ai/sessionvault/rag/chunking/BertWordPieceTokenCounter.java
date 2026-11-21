package com.jitendra.ai.sessionvault.rag.chunking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BertWordPieceTokenCounter implements TokenCounter {

    private static final int MAX_WORD_CODE_POINTS = 100;
    private final Set<String> vocabulary;

    public BertWordPieceTokenCounter(Path vocabularyPath) {
        if (vocabularyPath == null || !Files.isRegularFile(vocabularyPath) || !Files.isReadable(vocabularyPath)) {
            throw new IllegalStateException("Readable BERT tokenizer vocab.txt is required for the local RAG profile");
        }
        try {
            this.vocabulary = new HashSet<>(Files.readAllLines(vocabularyPath));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read BERT tokenizer vocabulary at " + vocabularyPath, e);
        }
    }

    @Override
    public int count(String text) {
        int count = 2;
        for (String token : basicTokens(text)) {
            count += wordPieceCount(token);
        }
        return count;
    }

    private int wordPieceCount(String word) {
        int[] codePoints = word.codePoints().toArray();
        if (codePoints.length > MAX_WORD_CODE_POINTS) {
            return 1;
        }

        int count = 0;
        int start = 0;
        while (start < codePoints.length) {
            int end = codePoints.length;
            boolean matched = false;
            while (end > start) {
                String piece = new String(codePoints, start, end - start);
                String candidate = start == 0 ? piece : "##" + piece;
                if (vocabulary.contains(candidate)) {
                    count++;
                    start = end;
                    matched = true;
                    break;
                }
                end--;
            }
            if (!matched) {
                return 1;
            }
        }
        return count;
    }

    private List<String> basicTokens(String text) {
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        int[] codePoints = normalized.codePoints().toArray();
        StringBuilder current = new StringBuilder();
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();

        for (int codePoint : codePoints) {
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || isControl(codePoint)) {
                flush(current, tokens);
            } else if (isChinese(codePoint) || isPunctuation(codePoint)) {
                flush(current, tokens);
                tokens.add(new String(Character.toChars(codePoint)));
            } else {
                current.appendCodePoint(codePoint);
            }
        }
        flush(current, tokens);
        return tokens;
    }

    private void flush(StringBuilder current, List<String> tokens) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private boolean isControl(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT;
    }

    private boolean isChinese(int codePoint) {
        return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
                || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
                || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
                || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
    }

    private boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return (codePoint >= 33 && codePoint <= 47)
                || (codePoint >= 58 && codePoint <= 64)
                || (codePoint >= 91 && codePoint <= 96)
                || (codePoint >= 123 && codePoint <= 126)
                || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }
}
