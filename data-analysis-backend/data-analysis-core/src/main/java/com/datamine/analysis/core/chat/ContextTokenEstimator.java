package com.datamine.analysis.core.chat;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class ContextTokenEstimator {

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;
    private static final int NAME_OVERHEAD_TOKENS = 2;
    private static final int SKILL_DEFINITION_TOKENS = 800;

    private final Encoding encoding;

    public ContextTokenEstimator() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.O200K_BASE);
    }

    public int estimateTextTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    public int estimateMessagesTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            total += MESSAGE_OVERHEAD_TOKENS;
            total += estimateTextTokens(message.getText());
            if (StringUtils.hasText(message.getMessageType().getValue())) {
                total += NAME_OVERHEAD_TOKENS;
            }
        }
        return total;
    }

    public int estimateToolDefinitionTokens(int toolCount) {
        if (toolCount <= 0) {
            return 0;
        }
        // Keep the estimate intentionally conservative so the progress bar trends safe.
        return toolCount * 240;
    }

    public int estimateSkillDefinitionTokens(int skillCount) {
        if (skillCount <= 0) {
            return 0;
        }
        return skillCount * SKILL_DEFINITION_TOKENS;
    }

    public int estimateReasoningTokens(List<Map<String, Object>> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Map<String, Object> rawMessage : rawMessages) {
            if (rawMessage == null) {
                continue;
            }
            Object reasoning = rawMessage.get("reasoning");
            if (reasoning instanceof String reasoningText) {
                total += estimateTextTokens(reasoningText);
            }
        }
        return total;
    }
}
