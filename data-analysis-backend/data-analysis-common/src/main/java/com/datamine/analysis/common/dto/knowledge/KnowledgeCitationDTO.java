package com.datamine.analysis.common.dto.knowledge;

import java.util.Map;

public record KnowledgeCitationDTO(
        Long documentId,
        String documentName,
        Integer chunkIndex,
        String content,
        Double score,
        Map<String, Object> metadata
) {
}
