package com.jitendra.ai.sessionvault.repository;

import com.jitendra.ai.sessionvault.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findBySessionId(UUID sessionId);
    List<Attachment> findByMessageId(UUID messageId);
}
