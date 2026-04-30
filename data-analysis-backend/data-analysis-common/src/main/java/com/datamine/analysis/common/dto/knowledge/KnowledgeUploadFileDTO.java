package com.datamine.analysis.common.dto.knowledge;

public record KnowledgeUploadFileDTO(
        String originalFilename,
        String contentType,
        byte[] content
) {
}
