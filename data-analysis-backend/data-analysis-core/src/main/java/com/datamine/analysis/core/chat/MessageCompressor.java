package com.datamine.analysis.core.chat;

import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.common.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageCompressor {

    private static final int KEEP_RECENT_MESSAGES = 4;      // 保留最近 4 条消息（2 轮对话）
    private static final int MIN_MESSAGES_TO_COMPRESS = 6;  // 至少 6 条消息才压缩
    private static final String SUMMARY_PREFIX = "之前对话摘要：";
    private static final String SUMMARY_SYSTEM_PROMPT = "请用简洁的语言总结以下对话内容，保留关键信息，例如数据库名、表名、查询条件和分析结论，控制在 200 字以内。";

    private final ChatModelFactory chatModelFactory;
    private final PersistentChatMemory chatMemory;
    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper;

    public CompressResult compress(Long userId, String sessionId) {
        // 获取完整的原始消息（包含 reasoning、steps、citations）
        List<Map<String, Object>> rawMessages = chatMemory.getRawMessages(userId, sessionId);

        if (rawMessages.size() < MIN_MESSAGES_TO_COMPRESS) {
            return new CompressResult(false, "消息数量不足，无需压缩", rawMessages.size(), rawMessages.size(), null);
        }

        log.info("Compressing conversation {} for user {}", sessionId, userId);

        // 分离 system 消息和对话消息
        List<Map<String, Object>> rawConversationMessages = new ArrayList<>();
        for (Map<String, Object> msg : rawMessages) {
            String role = (String) msg.get("role");
            if (!"system".equals(role)) {
                rawConversationMessages.add(msg);
            }
        }

        // 对话消息不足，无需压缩
        if (rawConversationMessages.size() <= KEEP_RECENT_MESSAGES) {
            return new CompressResult(false, "对话消息数量不足，无需压缩", rawMessages.size(), rawMessages.size(), null);
        }

        // 分割：需要压缩的 vs 保留的
        int splitIndex = rawConversationMessages.size() - KEEP_RECENT_MESSAGES;
        List<Map<String, Object>> rawToCompress = new ArrayList<>(rawConversationMessages.subList(0, splitIndex));
        List<Map<String, Object>> rawToKeep = new ArrayList<>(rawConversationMessages.subList(splitIndex, rawConversationMessages.size()));

        // 构建摘要请求
        String existingSummary = getExistingSummary(userId, sessionId);
        StringBuilder summaryBuilder = new StringBuilder();
        if (existingSummary != null && !existingSummary.isEmpty()) {
            summaryBuilder.append(SUMMARY_PREFIX)
                    .append('\n')
                    .append(existingSummary)
                    .append("\n\n");
        }
        summaryBuilder.append("新增的对话内容：\n");

        for (Map<String, Object> msg : rawToCompress) {
            String role = (String) msg.get("role");
            String content = (String) msg.get("content");
            if ("user".equals(role)) {
                summaryBuilder.append("用户: ").append(content).append('\n');
            } else if ("assistant".equals(role)) {
                summaryBuilder.append("助手: ").append(content).append('\n');
            }
        }

        try {
            ChatModel chatModel = chatModelFactory.getChatModel();
            String summary = chatModel.call(new Prompt(List.of(
                    new SystemMessage(SUMMARY_SYSTEM_PROMPT),
                    new UserMessage(summaryBuilder.toString())
            ))).getResult().getOutput().getText();

            if (summary == null || summary.isBlank()) {
                summary = "暂无可用摘要。";
            }

            // 序列化被压缩的原始消息（保留完整信息）
            String compressedMessagesJson = serializeRawMessages(rawToCompress);

            // 构建保留消息的 JSON（保留完整信息）
            String newMessagesJson = buildNewMessagesJson(summary, rawToKeep);

            // 保存摘要、压缩时间和被压缩的消息
            saveCompressionInfo(userId, sessionId, summary, compressedMessagesJson, newMessagesJson);

            int newCount = 1 + rawToKeep.size(); // 摘要 + 保留的消息
            return new CompressResult(true, "压缩成功", rawMessages.size(), newCount, summary);
        } catch (Exception e) {
            log.error("Failed to compress messages", e);
            return new CompressResult(false, "压缩失败，原会话已保留", rawMessages.size(), rawMessages.size(), null);
        }
    }

    private String getExistingSummary(Long userId, String sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .map(ChatSession::getSummary)
                .orElse(null);
    }

    private void saveCompressionInfo(Long userId, String sessionId, String summary, 
                                     String compressedMessagesJson, String newMessagesJson) {
        chatSessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
            session.setSummary(summary);
            session.setCompressedAt(LocalDateTime.now());
            session.setCompressedMessages(compressedMessagesJson);
            session.setMessages(newMessagesJson);
            chatSessionRepository.save(session);
        });
    }

    private String serializeRawMessages(List<Map<String, Object>> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception e) {
            log.error("Failed to serialize raw messages", e);
            return "[]";
        }
    }

    private String buildNewMessagesJson(String summary, List<Map<String, Object>> rawToKeep) {
        try {
            List<Map<String, Object>> newMessages = new ArrayList<>();
            
            // 添加摘要 SystemMessage
            Map<String, Object> summaryMsg = new LinkedHashMap<>();
            summaryMsg.put("role", "system");
            summaryMsg.put("content", SUMMARY_PREFIX + "\n" + summary);
            newMessages.add(summaryMsg);
            
            // 添加保留的消息（保留完整信息）
            newMessages.addAll(rawToKeep);
            
            return objectMapper.writeValueAsString(newMessages);
        } catch (Exception e) {
            log.error("Failed to build new messages JSON", e);
            return "[]";
        }
    }

    public record CompressResult(boolean compressed, String message, int beforeCount, int afterCount, String summary) {
    }
}
