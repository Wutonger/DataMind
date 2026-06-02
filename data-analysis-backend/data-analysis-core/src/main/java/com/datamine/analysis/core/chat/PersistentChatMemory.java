package com.datamine.analysis.core.chat;

import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;
import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.common.repository.ChatSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentChatMemory {

    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper;

    public void appendUserMessage(Long userId, String conversationId, Long connectionId, String content) {
        appendMessage(userId, conversationId, connectionId, new UserMessage(content), null, null, null);
    }

    public void appendAssistantMessage(Long userId,
                                       String conversationId,
                                       Long connectionId,
                                       Message message,
                                       List<Map<String, Object>> steps,
                                       String reasoning,
                                       List<KnowledgeCitationDTO> citations) {
        appendMessage(userId, conversationId, connectionId, message, steps, reasoning, citations);
    }

    public List<Message> getMessages(Long userId, String conversationId) {
        return findOwnedSession(userId, conversationId)
                .map(session -> toMessages(deserializeMessages(session.getMessages())))
                .orElseGet(ArrayList::new);
    }

    public List<Map<String, Object>> getRawMessages(Long userId, String conversationId) {
        return findOwnedSession(userId, conversationId)
                .map(session -> deserializeMessages(session.getMessages()))
                .orElseGet(ArrayList::new);
    }

    public void clear(Long userId, String conversationId) {
        findOwnedSession(userId, conversationId)
                .ifPresent(chatSessionRepository::delete);
    }

    public void setConnectionId(Long userId, String conversationId, Long connectionId) {
        ChatSession session = getOrCreateSession(userId, conversationId, connectionId);
        if (connectionId != null
                && (session.getConnectionId() == null || session.getConnectionId() <= 0)) {
            session.setConnectionId(connectionId);
            chatSessionRepository.save(session);
        }
    }

    public List<ChatSession> listSessions(Long userId, Long connectionId) {
        return chatSessionRepository.findByUserIdAndConnectionIdOrderByUpdatedAtDesc(userId, connectionId);
    }

    public Optional<ChatSession> findOwnedSession(Long userId, String conversationId) {
        return chatSessionRepository.findByIdAndUserId(conversationId, userId);
    }

    private void appendMessage(Long userId,
                               String conversationId,
                               Long connectionId,
                               Message message,
                               List<Map<String, Object>> steps,
                               String reasoning,
                               List<KnowledgeCitationDTO> citations) {
        ChatSession session = getOrCreateSession(userId, conversationId, connectionId);
        List<Map<String, Object>> existing = deserializeMessages(session.getMessages());
        Map<String, Object> messageMap = new LinkedHashMap<>();
        messageMap.put("role", message.getMessageType().getValue().toLowerCase());
        messageMap.put("content", message.getText());
        if (steps != null && !steps.isEmpty()) {
            messageMap.put("steps", steps);
        }
        if (reasoning != null && !reasoning.isBlank()) {
            messageMap.put("reasoning", reasoning);
        }
        if (citations != null && !citations.isEmpty()) {
            messageMap.put("citations", citations);
        }
        existing.add(messageMap);
        session.setMessages(serializeMessages(existing));
        chatSessionRepository.save(session);
    }

    private ChatSession getOrCreateSession(Long userId, String conversationId, Long connectionId) {
        Optional<ChatSession> ownedSession = chatSessionRepository.findByIdAndUserId(conversationId, userId);
        if (ownedSession.isPresent()) {
            return ownedSession.get();
        }

        chatSessionRepository.findById(conversationId).ifPresent(existing -> {
            if (!Objects.equals(existing.getUserId(), userId)) {
                throw new IllegalStateException("当前会话不存在或无权访问");
            }
        });

        ChatSession session = new ChatSession();
        session.setId(conversationId);
        session.setUserId(userId);
        session.setConnectionId(connectionId != null ? connectionId : 0L);
        session.setMessages("[]");
        return chatSessionRepository.save(session);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deserializeMessages(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize messages", e);
            return new ArrayList<>();
        }
    }

    private String serializeMessages(List<Map<String, Object>> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize messages", e);
            return "[]";
        }
    }

    private List<Message> toMessages(List<Map<String, Object>> raw) {
        List<Message> result = new ArrayList<>();
        for (Map<String, Object> message : raw) {
            String role = (String) message.get("role");
            String content = (String) message.get("content");
            if ("user".equals(role)) {
                result.add(new UserMessage(content));
            } else if ("assistant".equals(role)) {
                result.add(new AssistantMessage(content));
            } else if ("system".equals(role)) {
                result.add(new SystemMessage(content));
            }
        }
        return result;
    }
}
