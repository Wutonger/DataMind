package com.datamine.analysis.common.dto.chat;

import java.util.List;
import java.util.Map;

public record ChatHistoryResponse(
        List<Map<String, Object>> messages,
        String summary,
        String compressedAt,
        List<Map<String, Object>> compressedMessages
) {
}
