package com.datamine.analysis.common.dto.knowledge;

import java.util.List;

public record KnowledgeDocumentPreviewDTO(
        KnowledgeDocumentDTO document,
        List<KnowledgeDocumentChunkPreviewDTO> chunks
) {
}
