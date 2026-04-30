package com.datamine.analysis.core.service;

import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.common.util.SnowflakeIdGenerator;
import com.datamine.analysis.core.chat.ChatClientFactory;
import com.datamine.analysis.core.chat.MessageCompressor;
import com.datamine.analysis.core.chat.PersistentChatMemory;
import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatService {

    private final ChatClientFactory chatClientFactory;
    private final PersistentChatMemory chatMemory;
    private final MessageCompressor messageCompressor;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ChatService(ChatClientFactory chatClientFactory,
                       PersistentChatMemory chatMemory,
                       MessageCompressor messageCompressor,
                       AssistantAgentOrchestrator assistantAgentOrchestrator,
                       SnowflakeIdGenerator snowflakeIdGenerator) {
        this.chatClientFactory = chatClientFactory;
        this.chatMemory = chatMemory;
        this.messageCompressor = messageCompressor;
        this.assistantAgentOrchestrator = assistantAgentOrchestrator;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    public Flux<String> chatStream(String sessionId, Long connectionId, String userMessage) {
        String actualSessionId = sessionId;
        if (actualSessionId == null || actualSessionId.isEmpty()) {
            actualSessionId = String.valueOf(snowflakeIdGenerator.nextId());
        }
        final String finalSessionId = actualSessionId;

        if (connectionId != null) {
            chatMemory.setConnectionId(finalSessionId, connectionId);
        }

        chatMemory.add(finalSessionId, List.of(new UserMessage(userMessage)));

        ChatClient chatClient = chatClientFactory.getChatClient();
        return assistantAgentOrchestrator.orchestrateStream(
                finalSessionId,
                connectionId,
                userMessage,
                chatClient,
                chatMemory,
                result -> chatMemory.addWithSteps(
                        finalSessionId,
                        new AssistantMessage(result.content()),
                        result.steps(),
                        result.citations()
                )
        )
        .doOnComplete(() -> {
            log.info("Stream completed for session: {}", finalSessionId);
        })
        .doOnError(e -> {
            log.error("Stream error for session: {}", finalSessionId, e);
        })
        .onErrorResume(e -> {
            log.error("Stream error, returning error message", e);
            return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
        });
    }

    public List<Message> getHistory(String sessionId) {
        return chatMemory.get(sessionId);
    }

    public List<Map<String, Object>> getHistoryWithSteps(String sessionId) {
        return chatMemory.getRawMessages(sessionId);
    }

    public void clearHistory(String sessionId) {
        chatMemory.clear(sessionId);
    }

    public List<ChatSession> listSessions(Long connectionId) {
        return chatMemory.listSessions(connectionId);
    }

    public void refreshClient() {
        chatClientFactory.refreshClient();
    }

    public MessageCompressor.CompressResult compressHistory(String sessionId) {
        return messageCompressor.compress(sessionId);
    }
}
