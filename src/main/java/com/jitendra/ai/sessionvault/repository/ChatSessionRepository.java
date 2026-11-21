package com.jitendra.ai.sessionvault.repository;

import com.jitendra.ai.sessionvault.domain.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID>, JpaSpecificationExecutor<ChatSession> {

    @EntityGraph(attributePaths = {"tags"})
    Optional<ChatSession> findByExternalSessionId(String externalSessionId);

    @Query("SELECT s FROM ChatSession s LEFT JOIN FETCH s.tags WHERE s.id = :id")
    Optional<ChatSession> findByIdWithTags(@Param("id") UUID id);

    @Query("SELECT s FROM ChatSession s LEFT JOIN FETCH s.messages WHERE s.id = :id")
    Optional<ChatSession> findByIdWithMessages(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"tags"})
    Page<ChatSession> findByProjectNameIgnoreCase(String projectName, Pageable pageable);

    @EntityGraph(attributePaths = {"tags"})
    Page<ChatSession> findByProviderIgnoreCase(String provider, Pageable pageable);

    @Query("SELECT DISTINCT s FROM ChatSession s JOIN s.tags t WHERE LOWER(t.name) = LOWER(:tagName)")
    Page<ChatSession> findByTagName(@Param("tagName") String tagName, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"tags"})
    Page<ChatSession> findAll(Pageable pageable);
}
