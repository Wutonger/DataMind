package com.datamine.analysis.common.dto.chat;

public record ChatContextUsageResponse(
        int usedTokens,
        Integer maxContextTokens,
        int safeBudgetTokens,
        Integer usagePercent,
        int systemPromptTokens,
        int messageTokens,
        int messageCount,
        int compressedMessageCount,
        boolean compressed,
        String warningLevel
) {
}
