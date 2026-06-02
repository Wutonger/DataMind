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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageCompressor {

    private static final int MIN_MESSAGES_TO_COMPRESS = 5;
    private static final String SUMMARY_PREFIX = "之前对话摘要：";
    private static final String SUMMARY_SYSTEM_PROMPT = "请用简洁的语言总结以下对话内容，保留关键信息，例如数据库名、表名、查询条件和分析结论，控制在 200 字以内。";

    private final ChatModelFactory chatModelFactory;
    private final PersistentChatMemory chatMemory;
    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper;

    public CompressResult compress(Long userId, String sessionId) {
        List<Message> messages = chatMemory.getMessages(userId, sessionId);

        if (messages.size() <= MIN_MESSAGES_TO_COMPRESS) {
            return new CompressResult(false, "消息数量不足，无需压缩", messages.size(), messages.size());
        }

        log.info("Compressing conversation {} for user {}", sessionId, userId);

        List<Message> conversationMessages = new ArrayList<>();
        for (Message message : messages) {
            if (!(message instanceof SystemMessage)) {
                conversationMessages.add(message);
            }
        }

        if (conversationMessages.size() <= MIN_MESSAGES_TO_COMPRESS) {
            return new CompressResult(false, "对话消息数量不足，无需压缩", messages.size(), messages.size());
        }

        String existingSummary = getExistingSummary(userId, sessionId);
        StringBuilder summaryBuilder = new StringBuilder();
        if (existingSummary != null && !existingSummary.isEmpty()) {
            summaryBuilder.append(SUMMARY_PREFIX)
                    .append('\n')
                    .append(existingSummary)
                    .append("\n\n");
        }
        summaryBuilder.append("新增的对话内容：\n");

        for (Message message : conversationMessages) {
            if (message instanceof UserMessage) {
                summaryBuilder.append("用户: ").append(message.getText()).append('\n');
            } else if (message instanceof AssistantMessage) {
                summaryBuilder.append("助手: ").append(message.getText()).append('\n');
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

            saveSummary(userId, sessionId, summary);
            replaceMessages(userId, sessionId, List.of(new SystemMessage(SUMMARY_PREFIX + "\n" + summary)));
            return new CompressResult(true, "压缩成功", messages.size(), 1);
        } catch (Exception e) {
            log.error("Failed to compress messages", e);
            return new CompressResult(false, "压缩失败，原会话已保留", messages.size(), messages.size());
        }
    }

    private String getExistingSummary(Long userId, String sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .map(ChatSession::getSummary)
                .orElse(null);
    }

    private void saveSummary(Long userId, String sessionId, String summary) {
        chatSessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
            session.setSummary(summary);
            chatSessionRepository.save(session);
        });
    }

    private void replaceMessages(Long userId, String sessionId, List<Message> compressed) {
        chatSessionRepository.findByIdAndUserId(sessionId, userId).ifPresent(session -> {
            List<Map<String, String>> serialized = new ArrayList<>();
            for (Message message : compressed) {
                serialized.add(Map.of(
                        "role", message.getMessageType().getValue().toLowerCase(),
                        "content", message.getText()
                ));
            }
            try {
                session.setMessages(objectMapper.writeValueAsString(serialized));
                chatSessionRepository.save(session);
            } catch (Exception e) {
                log.error("Failed to replace messages after compression", e);
            }
        });
    }

    public record CompressResult(boolean compressed, String message, int beforeCount, int afterCount) {
    }
}
