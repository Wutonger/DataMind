package com.datamine.analysis.common.dto.knowledge;

import java.nio.file.Path;

public record KnowledgeDocumentFileDTO(
        String fileName,
        String contentType,
        Path path
) {
}
