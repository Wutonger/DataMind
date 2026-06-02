package com.datamine.analysis.core.service;

import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.common.util.SnowflakeIdGenerator;
import com.datamine.analysis.core.chat.ChatModelFactory;
import com.datamine.analysis.core.chat.MessageCompressor;
import com.datamine.analysis.core.chat.PersistentChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatService {

    private final ChatModelFactory chatModelFactory;
    private final PersistentChatMemory chatMemory;
    private final MessageCompressor messageCompressor;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ChatService(ChatModelFactory chatModelFactory,
                       PersistentChatMemory chatMemory,
                       MessageCompressor messageCompressor,
                       AssistantAgentOrchestrator assistantAgentOrchestrator,
                       SnowflakeIdGenerator snowflakeIdGenerator) {
        this.chatModelFactory = chatModelFactory;
        this.chatMemory = chatMemory;
        this.messageCompressor = messageCompressor;
        this.assistantAgentOrchestrator = assistantAgentOrchestrator;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    public Flux<String> chatStream(Long userId, String sessionId, Long connectionId, String userMessage) {
        if (connectionId == null) {
            return Flux.just("{\"error\": \"connectionId is required\"}");
        }

        String actualSessionId = sessionId;
        if (actualSessionId == null || actualSessionId.isEmpty()) {
            actualSessionId = String.valueOf(snowflakeIdGenerator.nextId());
        }
        final String finalSessionId = actualSessionId;

        chatMemory.setConnectionId(userId, finalSessionId, connectionId);
        chatMemory.appendUserMessage(userId, finalSessionId, connectionId, userMessage);
        List<Message> conversationMessages = chatMemory.getMessages(userId, finalSessionId);

        ChatModel chatModel = chatModelFactory.getChatModel();
        return assistantAgentOrchestrator.orchestrateStream(
                        finalSessionId,
                        userId,
                        connectionId,
                        userMessage,
                        conversationMessages,
                        chatModel,
                        chatModelFactory.isReasoningEnabled(),
                        result -> chatMemory.appendAssistantMessage(
                                userId,
                                finalSessionId,
                                connectionId,
                                new AssistantMessage(result.content()),
                                result.steps(),
                                result.reasoning(),
                                result.citations()
                        )
                )
                .doOnComplete(() -> log.info("Stream completed for session: {}", finalSessionId))
                .doOnError(e -> log.error("Stream error for session: {}", finalSessionId, e))
                .onErrorResume(e -> {
                    log.error("Stream error, returning error message", e);
                    return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
                });
    }

    public List<Message> getHistory(Long userId, String sessionId) {
        return chatMemory.getMessages(userId, sessionId);
    }

    public List<Map<String, Object>> getHistoryWithSteps(Long userId, String sessionId) {
        return chatMemory.getRawMessages(userId, sessionId);
    }

    public void clearHistory(Long userId, String sessionId) {
        chatMemory.clear(userId, sessionId);
    }

    public List<ChatSession> listSessions(Long userId, Long connectionId) {
        return chatMemory.listSessions(userId, connectionId);
    }

    public void refreshClient() {
        chatModelFactory.refresh();
    }

    public MessageCompressor.CompressResult compressHistory(Long userId, String sessionId) {
        return messageCompressor.compress(userId, sessionId);
    }
}
