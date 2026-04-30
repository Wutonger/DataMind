package com.datamine.analysis.common.dto.knowledge;

import java.util.Map;

public record KnowledgeDocumentChunkPreviewDTO(
        Integer chunkIndex,
        String content,
        Map<String, Object> metadata
) {
}
