package com.datamine.analysis.common.dto.knowledge;

import java.time.LocalDateTime;
public record KnowledgeDocumentDTO(
        Long id,
        Long connectionId,
        String name,
        String type,
        String status,
        Integer totalChunks,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
