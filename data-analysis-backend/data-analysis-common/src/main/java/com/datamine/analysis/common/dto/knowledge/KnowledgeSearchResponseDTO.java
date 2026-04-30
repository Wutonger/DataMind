package com.datamine.analysis.common.dto.knowledge;

import java.util.List;

public record KnowledgeSearchResponseDTO(
        String summary,
        List<KnowledgeCitationDTO> citations
) {
}
