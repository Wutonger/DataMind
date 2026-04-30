package com.datamine.analysis.common.dto.knowledge;

public record KnowledgeSearchRequestDTO(
        Long connectionId,
        String query
) {
}
