package com.datamine.analysis.core.service;

import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import com.datamine.analysis.agent.tool.AgentSkillHookFactory;
import com.datamine.analysis.common.dto.AiConfigDTO;
import com.datamine.analysis.common.dto.chat.ChatContextUsageResponse;
import com.datamine.analysis.common.dto.chat.ChatHistoryResponse;
import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.common.repository.ChatSessionRepository;
import com.datamine.analysis.common.util.SnowflakeIdGenerator;
import com.datamine.analysis.core.chat.ChatContextPromptResolver;
import com.datamine.analysis.core.chat.ChatModelFactory;
import com.datamine.analysis.core.chat.ContextTokenEstimator;
import com.datamine.analysis.core.chat.MessageCompressor;
import com.datamine.analysis.core.chat.PersistentChatMemory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatService {

    private static final int RESERVED_OUTPUT_TOKENS = 8192;
    private static final int SAFETY_MARGIN_TOKENS = 4096;

    private final ChatModelFactory chatModelFactory;
    private final AiConfigService aiConfigService;
    private final PersistentChatMemory chatMemory;
    private final MessageCompressor messageCompressor;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;
    private final AgentSkillHookFactory agentSkillHookFactory;
    private final ChatContextPromptResolver chatContextPromptResolver;
    private final ContextTokenEstimator contextTokenEstimator;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ChatSessionRepository chatSessionRepository;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ChatService(ChatModelFactory chatModelFactory,
                       AiConfigService aiConfigService,
                       PersistentChatMemory chatMemory,
                       MessageCompressor messageCompressor,
                       AssistantAgentOrchestrator assistantAgentOrchestrator,
                       AgentSkillHookFactory agentSkillHookFactory,
                       ChatContextPromptResolver chatContextPromptResolver,
                       ContextTokenEstimator contextTokenEstimator,
                       SnowflakeIdGenerator snowflakeIdGenerator,
                       ChatSessionRepository chatSessionRepository) {
        this.chatModelFactory = chatModelFactory;
        this.aiConfigService = aiConfigService;
        this.chatMemory = chatMemory;
        this.messageCompressor = messageCompressor;
        this.assistantAgentOrchestrator = assistantAgentOrchestrator;
        this.agentSkillHookFactory = agentSkillHookFactory;
        this.chatContextPromptResolver = chatContextPromptResolver;
        this.contextTokenEstimator = contextTokenEstimator;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.chatSessionRepository = chatSessionRepository;
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
                        result -> {
                            List<Message> updatedMessages = new ArrayList<>(conversationMessages);
                            updatedMessages.add(new AssistantMessage(result.content()));
                            List<Map<String, Object>> updatedRawMessages = new ArrayList<>(chatMemory.getRawMessages(userId, finalSessionId));
                            updatedRawMessages.add(buildAssistantRawMessage(result));
                            CompressionState compressionState = resolveCompressionState(userId, finalSessionId);
                            return estimateContextUsage(
                                    userId,
                                    connectionId,
                                    updatedMessages,
                                    updatedRawMessages,
                                    compressionState.compressedMessageCount(),
                                    compressionState.compressed(),
                                    summarizeSkillUsage(result.steps())
                            );
                        },
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

    public ChatHistoryResponse getHistoryWithCompressionInfo(Long userId, String sessionId) {
        List<Map<String, Object>> messages = chatMemory.getRawMessages(userId, sessionId);
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .map(session -> new ChatHistoryResponse(
                        messages,
                        session.getSummary(),
                        session.getCompressedAt() != null ? session.getCompressedAt().format(DATE_TIME_FORMATTER) : null,
                        deserializeCompressedMessages(session.getCompressedMessages())
                ))
                .orElseGet(() -> new ChatHistoryResponse(messages, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deserializeCompressedMessages(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize compressed messages", e);
            return null;
        }
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

    public ChatContextUsageResponse getContextUsage(Long userId, String sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalStateException("当前会话不存在或无权访问"));

        Long connectionId = session.getConnectionId();
        List<Message> messages = chatMemory.getMessages(userId, sessionId);
        List<Map<String, Object>> rawMessages = chatMemory.getRawMessages(userId, sessionId);
        List<Map<String, Object>> compressedMessages = deserializeCompressedMessages(session.getCompressedMessages());
        return estimateContextUsage(
                userId,
                connectionId,
                messages,
                rawMessages,
                compressedMessages == null ? 0 : compressedMessages.size(),
                session.getSummary() != null && !session.getSummary().isBlank()
        );
    }

    public ChatContextUsageResponse estimateContextUsage(Long userId,
                                                         Long connectionId,
                                                         List<Message> messages,
                                                         List<Map<String, Object>> rawMessages,
                                                         int compressedMessageCount,
                                                         boolean compressed) {
        return estimateContextUsage(
                userId,
                connectionId,
                messages,
                rawMessages,
                compressedMessageCount,
                compressed,
                new SkillUsageSummary(0, 0)
        );
    }

    private ChatContextUsageResponse estimateContextUsage(Long userId,
                                                          Long connectionId,
                                                          List<Message> messages,
                                                          List<Map<String, Object>> rawMessages,
                                                          int compressedMessageCount,
                                                          boolean compressed,
                                                          SkillUsageSummary skillUsage) {
        ChatContextPromptResolver.ChatPromptSnapshot promptSnapshot = chatContextPromptResolver.resolve(userId, connectionId, "");
        AiConfigDTO aiConfig = aiConfigService.getAiConfig();

        int systemPromptTokens = contextTokenEstimator.estimateTextTokens(promptSnapshot.prompt());
        int messageTokens = contextTokenEstimator.estimateMessagesTokens(messages);
        int reasoningTokens = contextTokenEstimator.estimateReasoningTokens(rawMessages);
        int skillTokens = contextTokenEstimator.estimateSkillDefinitionTokens(skillUsage.skillCount());
        int toolOverheadTokens = contextTokenEstimator.estimateToolDefinitionTokens(promptSnapshot.toolCount() + skillUsage.toolCount());
        int usedTokens = systemPromptTokens + messageTokens + reasoningTokens + skillTokens + toolOverheadTokens;

        Integer maxContextTokens = aiConfig.getMaxContextTokens();
        int safeBudgetTokens = 0;
        Integer usagePercent = null;
        if (maxContextTokens != null && maxContextTokens > 0) {
            safeBudgetTokens = Math.max(1, maxContextTokens - RESERVED_OUTPUT_TOKENS - SAFETY_MARGIN_TOKENS);
            usagePercent = Math.min(999, (int) Math.round((usedTokens * 100.0D) / safeBudgetTokens));
        }

        return new ChatContextUsageResponse(
                usedTokens,
                maxContextTokens,
                safeBudgetTokens,
                usagePercent,
                systemPromptTokens,
                messageTokens,
                messages == null ? 0 : messages.size(),
                compressedMessageCount,
                compressed,
                resolveWarningLevel(usagePercent)
        );
    }

    private String resolveWarningLevel(Integer usagePercent) {
        if (usagePercent == null) {
            return "normal";
        }
        if (usagePercent >= 80) {
            return "danger";
        }
        if (usagePercent >= 60) {
            return "warning";
        }
        return "normal";
    }

    private SkillUsageSummary summarizeSkillUsage(List<Map<String, Object>> steps) {
        if (steps == null || steps.isEmpty()) {
            return new SkillUsageSummary(0, 0);
        }

        int skillCount = 0;
        int toolCount = 0;
        for (Map<String, Object> step : steps) {
            if (step == null) {
                continue;
            }
            Object kindValue = step.get("kind");
            if (!(kindValue instanceof String kind) || !"skill".equalsIgnoreCase(kind)) {
                continue;
            }
            Object statusValue = step.get("status");
            if (statusValue instanceof String status && !"COMPLETED".equalsIgnoreCase(status)) {
                continue;
            }
            skillCount++;
            Object skillIdValue = step.get("skillId");
            String skillId = skillIdValue == null ? "" : String.valueOf(skillIdValue);
            toolCount += agentSkillHookFactory.resolveGroupedToolCount(skillId);
        }
        return new SkillUsageSummary(skillCount, toolCount);
    }

    private Map<String, Object> buildAssistantRawMessage(com.datamine.analysis.agent.model.ChatExecutionResult result) {
        Map<String, Object> rawMessage = new java.util.LinkedHashMap<>();
        rawMessage.put("role", "assistant");
        rawMessage.put("content", result.content());
        if (result.steps() != null && !result.steps().isEmpty()) {
            rawMessage.put("steps", result.steps());
        }
        if (result.reasoning() != null && !result.reasoning().isBlank()) {
            rawMessage.put("reasoning", result.reasoning());
        }
        if (result.citations() != null && !result.citations().isEmpty()) {
            rawMessage.put("citations", result.citations());
        }
        return rawMessage;
    }

    private CompressionState resolveCompressionState(Long userId, String sessionId) {
        ChatSession session = chatSessionRepository.findByIdAndUserId(sessionId, userId).orElse(null);
        if (session == null) {
            return new CompressionState(0, false);
        }
        List<Map<String, Object>> compressedMessages = deserializeCompressedMessages(session.getCompressedMessages());
        return new CompressionState(
                compressedMessages == null ? 0 : compressedMessages.size(),
                session.getSummary() != null && !session.getSummary().isBlank()
        );
    }

    private record SkillUsageSummary(int skillCount, int toolCount) {
    }

    private record CompressionState(int compressedMessageCount, boolean compressed) {
    }
}
