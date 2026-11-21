package com.jitendra.ai.sessionvault.repository;

import com.jitendra.ai.sessionvault.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderBySequenceNumberAsc(UUID sessionId);

    int countBySessionId(UUID sessionId);

    @Query(
        value = """
            SELECT m.* FROM chat_messages m
            WHERE m.fts_vector @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank_cd(m.fts_vector, plainto_tsquery('english', :query)) DESC
            """,
        countQuery = """
            SELECT count(*) FROM chat_messages m
            WHERE m.fts_vector @@ plainto_tsquery('english', :query)
            """,
        nativeQuery = true
    )
    Page<ChatMessage> searchByFullText(@Param("query") String query, Pageable pageable);

    @Query(
        value = """
            SELECT m.* FROM chat_messages m
            WHERE m.session_id = :sessionId
              AND m.fts_vector @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank_cd(m.fts_vector, plainto_tsquery('english', :query)) DESC
            """,
        countQuery = """
            SELECT count(*) FROM chat_messages m
            WHERE m.session_id = :sessionId
              AND m.fts_vector @@ plainto_tsquery('english', :query)
            """,
        nativeQuery = true
    )
    Page<ChatMessage> searchInSessionByFullText(@Param("sessionId") UUID sessionId, @Param("query") String query, Pageable pageable);
}
