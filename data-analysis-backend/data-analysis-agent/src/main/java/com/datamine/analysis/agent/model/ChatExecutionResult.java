package com.datamine.analysis.agent.model;

import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;

import java.util.List;
import java.util.Map;

/**
 * 封装聊天场景执行完成后的结果，供会话持久化与前端响应复用。
 */
public record ChatExecutionResult(
        String content,
        List<Map<String, Object>> steps,
        String reasoning,
        List<KnowledgeCitationDTO> citations
) {
}
