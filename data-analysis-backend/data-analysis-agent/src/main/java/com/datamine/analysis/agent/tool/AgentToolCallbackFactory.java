package com.datamine.analysis.agent.tool;

import com.datamine.analysis.mcp.client.McpClient;
import com.datamine.analysis.skills.input.KnowledgeSearchInput;
import com.datamine.analysis.skills.input.SaveChartReportInput;
import com.datamine.analysis.skills.input.SaveMarkdownReportInput;
import com.datamine.analysis.skills.tool.KnowledgeSearchToolService;
import com.datamine.analysis.skills.tool.SaveChartReportToolService;
import com.datamine.analysis.skills.tool.SaveMarkdownReportToolService;
import com.datamine.analysis.skills.tool.ToolExecutionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
public class AgentToolCallbackFactory {

    private final McpClient mcpClient;
    private final ObjectMapper objectMapper;
    private final ToolExecutionSupport toolExecutionSupport;
    private final KnowledgeSearchToolService knowledgeSearchToolService;
    private final SaveChartReportToolService saveChartReportToolService;
    private final SaveMarkdownReportToolService saveMarkdownReportToolService;

    /**
     * 统一组装 MCP 工具与项目内置 Tool，供 Agent 按需调用。
     */
    public List<ToolCallback> buildCallbacks(Long connectionId,
                                             String userInput,
                                             ChatClient chatClient) {
        if (connectionId == null) {
            return List.of();
        }

        List<ToolCallback> toolCallbacks = new ArrayList<>();

        if (mcpClient.isInitialized()) {
            mcpClient.getSyncClient().ifPresent(syncClient -> {
                SyncMcpToolCallbackProvider provider = new SyncMcpToolCallbackProvider(syncClient);
                for (ToolCallback callback : provider.getToolCallbacks()) {
                    toolCallbacks.add(new ConnectionAwareToolCallback(callback, connectionId, objectMapper, toolExecutionSupport));
                }
            });
        }

        toolCallbacks.add(buildKnowledgeSearchTool(connectionId, userInput));
        toolCallbacks.add(buildSaveChartReportTool(connectionId, userInput));
        toolCallbacks.add(buildSaveMarkdownReportTool(connectionId, userInput));
        return toolCallbacks;
    }

    private ToolCallback buildKnowledgeSearchTool(Long connectionId, String userInput) {
        return FunctionToolCallback
                .builder(
                        KnowledgeSearchToolService.TOOL_NAME,
                        (BiFunction<KnowledgeSearchInput, ToolContext, Map<String, Object>>) (input, toolContext) ->
                                knowledgeSearchToolService.execute(connectionId, userInput, input, toolContext)
                )
                .description(KnowledgeSearchToolService.TOOL_DESCRIPTION)
                .inputType(KnowledgeSearchInput.class)
                .build();
    }

    private ToolCallback buildSaveChartReportTool(Long connectionId, String userInput) {
        return FunctionToolCallback
                .builder(
                        SaveChartReportToolService.TOOL_NAME,
                        (BiFunction<SaveChartReportInput, ToolContext, Map<String, Object>>) (input, toolContext) ->
                                saveChartReportToolService.execute(connectionId, userInput, input, toolContext)
                )
                .description(SaveChartReportToolService.TOOL_DESCRIPTION)
                .inputType(SaveChartReportInput.class)
                .build();
    }

    private ToolCallback buildSaveMarkdownReportTool(Long connectionId, String userInput) {
        return FunctionToolCallback
                .builder(
                        SaveMarkdownReportToolService.TOOL_NAME,
                        (BiFunction<SaveMarkdownReportInput, ToolContext, Map<String, Object>>) (input, toolContext) ->
                                saveMarkdownReportToolService.execute(connectionId, userInput, input, toolContext)
                )
                .description(SaveMarkdownReportToolService.TOOL_DESCRIPTION)
                .inputType(SaveMarkdownReportInput.class)
                .build();
    }
}
