package com.datamine.analysis.skills.tool;

import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchRequestDTO;
import com.datamine.analysis.common.dto.knowledge.KnowledgeSearchResponseDTO;
import com.datamine.analysis.common.service.KnowledgeBaseService;
import com.datamine.analysis.skills.input.KnowledgeSearchInput;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeSearchToolService {

    public static final String TOOL_NAME = "knowledge_search";
    public static final String TOOL_DESCRIPTION = "在当前连接的知识库中检索业务规则、指标口径和文档依据，并返回摘要与引用片段";

    private final KnowledgeBaseService knowledgeBaseService;
    private final ToolExecutionSupport toolExecutionSupport;

    public Map<String, Object> execute(Long connectionId,
                                       String userInput,
                                       KnowledgeSearchInput input,
                                       ToolContext toolContext) {
        if (connectionId == null) {
            return toolExecutionSupport.failure("No active database connection is available for knowledge search");
        }

        String query = input != null && StringUtils.hasText(input.query())
                ? input.query().trim()
                : userInput;

        KnowledgeSearchResponseDTO response = knowledgeBaseService.search(
                new KnowledgeSearchRequestDTO(connectionId, query));
        toolExecutionSupport.rememberKnowledgeContext(toolContext, query, response.summary(), response.citations());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", response.summary());
        data.put("citations", response.citations());
        return toolExecutionSupport.success(response.summary(), data);
    }
}
