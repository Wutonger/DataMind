package com.datamine.analysis.core.service;

import com.datamine.analysis.agent.model.SqlExecutionResult;
import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import com.datamine.analysis.core.chat.ChatClientFactory;
import com.datamine.analysis.mcp.client.McpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SqlService {

    private final ChatClientFactory chatClientFactory;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;
    private final McpClient mcpClient;

    public Map<String, Object> executeQuery(Long connectionId, String sql) {
        return mcpClient.dbExecute(connectionId, sql);
    }

    public Map<String, String> generateSql(Long connectionId, String question) {
        SqlExecutionResult result = assistantAgentOrchestrator.generateSql(
                connectionId,
                question,
                chatClientFactory.getChatClient()
        );
        return Map.of(
                "sql", result.sql(),
                "workflowRunId", result.workflowRunId()
        );
    }
}
