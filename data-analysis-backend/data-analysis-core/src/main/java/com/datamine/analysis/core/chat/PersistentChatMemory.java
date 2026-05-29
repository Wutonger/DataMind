package com.datamine.analysis.core.chat;

import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;
import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.common.repository.ChatSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentChatMemory implements ChatMemory {

    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void add(String conversationId, List<Message> messages) {
        ChatSession session = chatSessionRepository.findById(conversationId)
                .orElseGet(() -> {
                    ChatSession s = new ChatSession();
                    s.setId(conversationId);
                    s.setMessages("[]");
                    s.setConnectionId(0L);
                    return s;
                });

        List<Map<String, Object>> existing = deserializeMessages(session.getMessages());
        for (Message msg : messages) {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("role", msg.getMessageType().getValue().toLowerCase());
            msgMap.put("content", msg.getText());
            existing.add(msgMap);
        }
        session.setMessages(serializeMessages(existing));
        chatSessionRepository.save(session);
    }

    public void addWithSteps(String conversationId,
                             Message message,
                             List<Map<String, Object>> steps,
                             String reasoning,
                             List<KnowledgeCitationDTO> citations) {
        ChatSession session = chatSessionRepository.findById(conversationId)
                .orElseGet(() -> {
                    ChatSession s = new ChatSession();
                    s.setId(conversationId);
                    s.setMessages("[]");
                    s.setConnectionId(0L);
                    return s;
                });

        List<Map<String, Object>> existing = deserializeMessages(session.getMessages());
        Map<String, Object> msgMap = new LinkedHashMap<>();
        msgMap.put("role", message.getMessageType().getValue().toLowerCase());
        msgMap.put("content", message.getText());
        if (steps != null && !steps.isEmpty()) {
            msgMap.put("steps", steps);
        }
        if (reasoning != null && !reasoning.isBlank()) {
            msgMap.put("reasoning", reasoning);
        }
        if (citations != null && !citations.isEmpty()) {
            msgMap.put("citations", citations);
        }
        existing.add(msgMap);
        session.setMessages(serializeMessages(existing));
        chatSessionRepository.save(session);
    }

    @Override
    public List<Message> get(String conversationId) {
        return chatSessionRepository.findById(conversationId)
                .map(session -> toMessages(deserializeMessages(session.getMessages())))
                .orElse(new ArrayList<>());
    }

    public List<Map<String, Object>> getRawMessages(String conversationId) {
        return chatSessionRepository.findById(conversationId)
                .map(session -> deserializeMessages(session.getMessages()))
                .orElse(new ArrayList<>());
    }

    @Override
    public void clear(String conversationId) {
        chatSessionRepository.deleteById(conversationId);
    }

    public void setConnectionId(String conversationId, Long connectionId) {
        ChatSession session = chatSessionRepository.findById(conversationId)
                .orElseGet(() -> {
                    ChatSession s = new ChatSession();
                    s.setId(conversationId);
                    s.setConnectionId(connectionId);
                    s.setMessages("[]");
                    return s;
                });
        if (!Objects.equals(session.getConnectionId(), connectionId)
                && (session.getConnectionId() == null || session.getConnectionId() <= 0)) {
            session.setConnectionId(connectionId);
        }
        chatSessionRepository.save(session);
    }

    public List<ChatSession> listSessions(Long connectionId) {
        return chatSessionRepository.findByConnectionIdOrderByUpdatedAtDesc(connectionId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deserializeMessages(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
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
        for (Map<String, Object> m : raw) {
            String role = (String) m.get("role");
            String content = (String) m.get("content");
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
