package com.jitendra.ai.sessionvault.rag.chunking;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import com.jitendra.ai.sessionvault.domain.MessageRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConversationChunker {

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(`{3,}|~{3,}).*$");

    private final TokenCounter tokenCounter;
    private final int maxTokens;
    private final int overlapTokens;
    private final String documentPrefix;

    public ConversationChunker(TokenCounter tokenCounter, int maxTokens, int overlapTokens) {
        this(tokenCounter, maxTokens, overlapTokens, "");
    }

    public ConversationChunker(TokenCounter tokenCounter, int maxTokens, int overlapTokens, String documentPrefix) {
        if (maxTokens < 1 || overlapTokens < 0 || overlapTokens >= maxTokens) {
            throw new IllegalArgumentException("Invalid RAG chunk token limits");
        }
        this.tokenCounter = tokenCounter;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
        this.documentPrefix = documentPrefix == null ? "" : documentPrefix;
    }

    public List<Chunk> chunk(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        List<ChatMessage> ordered = messages.stream()
                .sorted(java.util.Comparator.comparingInt(ChatMessage::getSequenceNumber))
                .toList();
        List<Turn> turns = groupTurns(ordered);
        List<List<Atom>> chunks = new ArrayList<>();
        List<Atom> current = new ArrayList<>();

        for (Turn turn : turns) {
            int turnTokens = count(turn.atoms());
            if (turnTokens <= maxTokens && countWith(current, turn.atoms()) <= maxTokens) {
                current.addAll(turn.atoms());
                continue;
            }

            if (!current.isEmpty()) {
                chunks.add(List.copyOf(current));
                current.clear();
            }

            if (turnTokens > maxTokens) {
                for (Atom atom : turn.atoms()) {
                    if (!current.isEmpty() && countWith(current, List.of(atom)) > maxTokens) {
                        chunks.add(List.copyOf(current));
                        current.clear();
                    }
                    current.add(atom);
                    if (tokenCounter.count(atom.text()) > maxTokens) {
                        chunks.add(List.copyOf(current));
                        current.clear();
                    }
                }
            } else {
                current.addAll(turn.atoms());
            }
        }
        if (!current.isEmpty()) {
            chunks.add(List.copyOf(current));
        }

        return addOverlap(chunks);
    }

    private List<Turn> groupTurns(List<ChatMessage> messages) {
        List<Turn> turns = new ArrayList<>();
        List<ChatMessage> current = new ArrayList<>();
        boolean hasNonUser = false;

        for (ChatMessage message : messages) {
            if (message.getRole() == MessageRole.USER && hasNonUser && !current.isEmpty()) {
                turns.add(toTurn(current));
                current.clear();
                hasNonUser = false;
            }
            current.add(message);
            if (message.getRole() != MessageRole.USER) {
                hasNonUser = true;
            }
        }
        if (!current.isEmpty()) {
            turns.add(toTurn(current));
        }
        return turns;
    }

    private Turn toTurn(List<ChatMessage> messages) {
        List<Atom> atoms = new ArrayList<>();
        Set<String> roles = new LinkedHashSet<>();
        for (ChatMessage message : messages) {
            roles.add(message.getRole().name());
            List<String> paragraphs = splitParagraphs(message.getContent());
            if (paragraphs.isEmpty()) {
                paragraphs = List.of("");
            }
            for (int i = 0; i < paragraphs.size(); i++) {
                String text = (i == 0 ? "[" + message.getRole().name() + "]\n" : "") + paragraphs.get(i);
                atoms.add(new Atom(text, message.getSequenceNumber(), message.getSequenceNumber(),
                        Set.of(message.getRole().name())));
            }
        }
        String roleSequence = String.join(">", roles);
        return new Turn(atoms.stream()
                .map(atom -> new Atom(atom.text(), atom.startSequence(), atom.endSequence(), Set.of(roleSequence)))
                .toList());
    }

    private List<String> splitParagraphs(String content) {
        List<String> result = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        String fence = null;
        for (String line : content.split("(?<=\\n)", -1)) {
            String trimmed = line.stripTrailing();
            Matcher matcher = CODE_FENCE.matcher(trimmed);
            String marker = matcher.matches() ? matcher.group(1) : null;

            if (fence == null && marker != null) {
                fence = marker.substring(0, 3);
                paragraph.append(line);
                continue;
            }
            if (fence != null) {
                paragraph.append(line);
                if (marker != null && marker.startsWith(fence)) {
                    result.add(paragraph.toString());
                    paragraph.setLength(0);
                    fence = null;
                }
                continue;
            }
            if (line.isBlank()) {
                paragraph.append(line);
                if (!paragraph.isEmpty()) {
                    result.add(paragraph.toString());
                    paragraph.setLength(0);
                }
            } else {
                paragraph.append(line);
            }
        }
        if (!paragraph.isEmpty()) {
            result.add(paragraph.toString());
        }
        return result;
    }

    private List<Chunk> addOverlap(List<List<Atom>> source) {
        List<Chunk> result = new ArrayList<>();
        List<Atom> previous = List.of();
        int index = 0;

        for (List<Atom> raw : source) {
            List<Atom> atoms = new ArrayList<>();
            if (!previous.isEmpty() && overlapTokens > 0) {
                List<Atom> overlap = overlapFrom(previous);
                if (countWith(overlap, raw) <= maxTokens) {
                    atoms.addAll(overlap);
                }
            }
            atoms.addAll(raw);
            String content = join(atoms);
            Set<String> roles = new LinkedHashSet<>();
            atoms.forEach(atom -> roles.addAll(atom.roles()));
            int start = atoms.stream().mapToInt(Atom::startSequence).min().orElse(0);
            int end = atoms.stream().mapToInt(Atom::endSequence).max().orElse(0);
            int count = tokenCounter.count(documentPrefix + content);
            result.add(new Chunk(index++, String.join(">", roles), content, count,
                    start, end, count > maxTokens, sha256(content)));
            previous = raw;
        }
        return result;
    }

    private List<Atom> overlapFrom(List<Atom> atoms) {
        List<Atom> overlap = new ArrayList<>();
        int count = 0;
        for (int i = atoms.size() - 1; i >= 0; i--) {
            Atom atom = atoms.get(i);
            int atomTokens = tokenCounter.count(atom.text());
            if (count + atomTokens > overlapTokens) {
                break;
            }
            overlap.add(0, atom);
            count += atomTokens;
        }
        return overlap;
    }

    private int count(List<Atom> atoms) {
        return tokenCounter.count(documentPrefix + join(atoms));
    }

    private int countWith(List<Atom> left, List<Atom> right) {
        List<Atom> combined = new ArrayList<>(left);
        combined.addAll(right);
        return count(combined);
    }

    private String join(List<Atom> atoms) {
        return atoms.stream().map(Atom::text).collect(java.util.stream.Collectors.joining("\n"));
    }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record Atom(String text, int startSequence, int endSequence, Set<String> roles) {}
    private record Turn(List<Atom> atoms) {}

    public record Chunk(
            int index,
            String roleSequence,
            String content,
            int tokenCount,
            int startSequence,
            int endSequence,
            boolean oversized,
            String contentHash) {}
}
