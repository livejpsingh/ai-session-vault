package com.jitendra.ai.sessionvault.service;

import com.jitendra.ai.sessionvault.domain.ChatSession;
import com.jitendra.ai.sessionvault.domain.SessionStatus;
import com.jitendra.ai.sessionvault.domain.Tag;
import com.jitendra.ai.sessionvault.dto.ChatSessionDto;
import com.jitendra.ai.sessionvault.dto.CreateSessionRequest;
import com.jitendra.ai.sessionvault.exception.DuplicateResourceException;
import com.jitendra.ai.sessionvault.exception.ResourceNotFoundException;
import com.jitendra.ai.sessionvault.repository.ChatMessageRepository;
import com.jitendra.ai.sessionvault.repository.ChatSessionRepository;
import com.jitendra.ai.sessionvault.repository.TagRepository;
import com.jitendra.ai.sessionvault.service.impl.SessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private SessionServiceImpl sessionService;

    private ChatSession testSession;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        testSession = ChatSession.builder()
                .id(testId)
                .externalSessionId("ext-conv-123")
                .title("Architectural Review")
                .description("Production readiness check")
                .provider("google-antigravity")
                .modelName("gemini-2.5-pro")
                .projectName("SessionVault")
                .workspacePath("/workspace/project")
                .status(SessionStatus.ACTIVE)
                .tags(new HashSet<>(Collections.singletonList(new Tag("architecture"))))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createSession successfully creates and saves a session")
    void createSession_Success() {
        CreateSessionRequest request = CreateSessionRequest.builder()
                .externalSessionId("ext-conv-new")
                .title("New Session")
                .tags(Set.of("spring", "security"))
                .build();

        when(sessionRepository.findByExternalSessionId("ext-conv-new")).thenReturn(Optional.empty());
        when(tagRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(i -> {
            ChatSession s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        ChatSessionDto result = sessionService.createSession(request);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("New Session");
        assertThat(result.getExternalSessionId()).isEqualTo("ext-conv-new");
        verify(sessionRepository).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("createSession throws DuplicateResourceException if externalSessionId already exists")
    void createSession_Duplicate_ThrowsException() {
        CreateSessionRequest request = CreateSessionRequest.builder()
                .externalSessionId("ext-conv-123")
                .title("Duplicate Session")
                .build();

        when(sessionRepository.findByExternalSessionId("ext-conv-123")).thenReturn(Optional.of(testSession));

        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("getSessionById returns session DTO when session exists")
    void getSessionById_Found() {
        when(sessionRepository.findByIdWithTags(testId)).thenReturn(Optional.of(testSession));
        when(messageRepository.countBySessionId(testId)).thenReturn(5);

        ChatSessionDto dto = sessionService.getSessionById(testId, false);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(testId);
        assertThat(dto.getTitle()).isEqualTo("Architectural Review");
        assertThat(dto.getMessageCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getSessionById throws ResourceNotFoundException when session does not exist")
    void getSessionById_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(sessionRepository.findByIdWithTags(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSessionById(unknownId, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("getSessionByExternalId returns session DTO when external ID exists")
    void getSessionByExternalId_Found() {
        when(sessionRepository.findByExternalSessionId("ext-conv-123")).thenReturn(Optional.of(testSession));
        when(messageRepository.countBySessionId(testId)).thenReturn(12);

        ChatSessionDto dto = sessionService.getSessionByExternalId("ext-conv-123", false);

        assertThat(dto).isNotNull();
        assertThat(dto.getExternalSessionId()).isEqualTo("ext-conv-123");
        assertThat(dto.getMessageCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("listSessions returns paged DTOs")
    void listSessions_ReturnsPagedResults() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ChatSession> page = new PageImpl<>(List.of(testSession), pageable, 1);
        when(sessionRepository.findAll(pageable)).thenReturn(page);

        Page<ChatSessionDto> results = sessionService.listSessions(pageable, null, null, null);

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).getTitle()).isEqualTo("Architectural Review");
    }

    @Test
    @DisplayName("deleteSession deletes existing session")
    void deleteSession_Success() {
        when(sessionRepository.existsById(testId)).thenReturn(true);

        sessionService.deleteSession(testId);

        verify(sessionRepository).deleteById(testId);
    }

    @Test
    @DisplayName("deleteSession throws ResourceNotFoundException for non-existent session")
    void deleteSession_NotFound_ThrowsException() {
        UUID missingId = UUID.randomUUID();
        when(sessionRepository.existsById(missingId)).thenReturn(false);

        assertThatThrownBy(() -> sessionService.deleteSession(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sessionRepository, never()).deleteById(missingId);
    }
}
