package com.datamine.analysis.common.dto.knowledge;

import java.util.List;

public record KnowledgeUploadRequestDTO(
        Long connectionId,
        List<KnowledgeUploadFileDTO> files
) {
}
