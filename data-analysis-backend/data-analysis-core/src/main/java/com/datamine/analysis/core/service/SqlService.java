package com.datamine.analysis.core.service;

import com.datamine.analysis.agent.model.SqlExecutionResult;
import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import com.datamine.analysis.core.chat.ChatModelFactory;
import com.datamine.analysis.mcp.client.McpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SqlService {

    private final ChatModelFactory chatModelFactory;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;
    private final McpClient mcpClient;

    public Map<String, Object> executeQuery(Long connectionId, String sql) {
        return mcpClient.dbExecute(connectionId, sql);
    }

    public Map<String, String> generateSql(Long userId, Long connectionId, String question) {
        SqlExecutionResult result = assistantAgentOrchestrator.generateSql(
                userId,
                connectionId,
                question,
                chatModelFactory.getChatModel()
        );
        validateGeneratedSql(connectionId, result.sql());
        return Map.of(
                "sql", result.sql(),
                "workflowRunId", result.workflowRunId()
        );
    }

    private void validateGeneratedSql(Long connectionId, String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalStateException("未生成有效 SQL，请重试");
        }

        Map<String, Object> validationResult = mcpClient.dbExecute(connectionId, buildValidationSql(sql));
        if (!Boolean.TRUE.equals(validationResult.get("error"))) {
            return;
        }

        String errorMessage = String.valueOf(validationResult.getOrDefault("message", "unknown error"));
        throw new IllegalStateException("生成的 SQL 未通过数据库校验: " + errorMessage);
    }

    private String buildValidationSql(String sql) {
        String normalized = sql.trim();
        String upper = normalized.toUpperCase();
        if (upper.startsWith("SELECT ") || upper.startsWith("WITH ")) {
            return "EXPLAIN " + normalized;
        }
        return normalized;
    }
}
