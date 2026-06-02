package com.datamine.analysis.agent.tool;

import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
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
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 统一负责 Agent 工具的构建、场景筛选与 skill hook 装配。
 */
@Component
@RequiredArgsConstructor
public class AgentToolsetFactory {

    private final McpClient mcpClient;
    private final ObjectMapper objectMapper;
    private final ToolExecutionSupport toolExecutionSupport;
    private final KnowledgeSearchToolService knowledgeSearchToolService;
    private final SaveChartReportToolService saveChartReportToolService;
    private final SaveMarkdownReportToolService saveMarkdownReportToolService;
    private final AgentSkillHookFactory agentSkillHookFactory;

    public AgentToolset createChatToolset(Long userId, Long connectionId, String userInput) {
        return buildToolset(buildAllCallbacks(userId, connectionId, userInput));
    }

    public AgentToolset createSqlToolset(Long userId, Long connectionId, String userInput) {
        return buildToolset(buildAllCallbacks(userId, connectionId, userInput).stream()
                .filter(callback -> {
                    String toolName = callback.getToolDefinition().name();
                    return isKnowledgeTool(toolName)
                            || isSchemaTool(toolName)
                            || isDbExecuteTool(toolName);
                })
                .toList());
    }

    public AgentToolset createReportToolset(Long userId, Long connectionId, String userInput) {
        return buildToolset(buildAllCallbacks(userId, connectionId, userInput).stream()
                .filter(callback -> {
                    String toolName = callback.getToolDefinition().name();
                    return isKnowledgeTool(toolName)
                            || isSchemaTool(toolName)
                            || isDbExecuteTool(toolName)
                            || isReportArtifactTool(toolName);
                })
                .toList());
    }

    /**
     * 统一组装 MCP 工具与项目内置工具。
     */
    private List<ToolCallback> buildAllCallbacks(Long userId, Long connectionId, String userInput) {
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
        toolCallbacks.add(buildSaveChartReportTool(userId, connectionId, userInput));
        toolCallbacks.add(buildSaveMarkdownReportTool(userId, connectionId, userInput));
        return toolCallbacks;
    }

    private AgentToolset buildToolset(List<ToolCallback> sceneCallbacks) {
        return new AgentToolset(
                sceneCallbacks,
                agentSkillHookFactory.resolveBaseTools(sceneCallbacks),
                agentSkillHookFactory.buildHook(sceneCallbacks),
                containsSchemaTool(sceneCallbacks),
                containsReportArtifactTool(sceneCallbacks)
        );
    }

    private boolean containsSchemaTool(List<ToolCallback> callbacks) {
        return callbacks.stream().anyMatch(callback -> isSchemaTool(callback.getToolDefinition().name()));
    }

    private boolean containsReportArtifactTool(List<ToolCallback> callbacks) {
        return callbacks.stream().anyMatch(callback -> isReportArtifactTool(callback.getToolDefinition().name()));
    }

    private boolean isKnowledgeTool(String toolName) {
        return matches(toolName, "knowledge_search");
    }

    private boolean isSchemaTool(String toolName) {
        String normalized = normalize(toolName);
        return "db_get_schema".equals(normalized)
                || "db_list_tables".equals(normalized)
                || "db_get_columns".equals(normalized);
    }

    private boolean isDbExecuteTool(String toolName) {
        return matches(toolName, "db_execute");
    }

    private boolean isReportArtifactTool(String toolName) {
        return matches(toolName, "save_chart_report") || matches(toolName, "save_markdown_report");
    }

    private boolean matches(String toolName, String expected) {
        return expected.equals(normalize(toolName));
    }

    private String normalize(String toolName) {
        return ToolNameNormalizer.canonicalize(toolName);
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

    private ToolCallback buildSaveChartReportTool(Long userId, Long connectionId, String userInput) {
        return FunctionToolCallback
                .builder(
                        SaveChartReportToolService.TOOL_NAME,
                        (BiFunction<SaveChartReportInput, ToolContext, Map<String, Object>>) (input, toolContext) ->
                                saveChartReportToolService.execute(userId, connectionId, userInput, input, toolContext)
                )
                .description(SaveChartReportToolService.TOOL_DESCRIPTION)
                .inputType(SaveChartReportInput.class)
                .build();
    }

    private ToolCallback buildSaveMarkdownReportTool(Long userId, Long connectionId, String userInput) {
        return FunctionToolCallback
                .builder(
                        SaveMarkdownReportToolService.TOOL_NAME,
                        (BiFunction<SaveMarkdownReportInput, ToolContext, Map<String, Object>>) (input, toolContext) ->
                                saveMarkdownReportToolService.execute(userId, connectionId, userInput, input, toolContext)
                )
                .description(SaveMarkdownReportToolService.TOOL_DESCRIPTION)
                .inputType(SaveMarkdownReportInput.class)
                .build();
    }

    /**
     * 按场景整理后的工具集合。
     */
    public record AgentToolset(List<ToolCallback> sceneCallbacks,
                               List<ToolCallback> baseCallbacks,
                               SkillsAgentHook skillHook,
                               boolean hasSchemaTools,
                               boolean hasReportArtifactTools) {

        public AgentToolset {
            sceneCallbacks = List.copyOf(sceneCallbacks);
            baseCallbacks = List.copyOf(baseCallbacks);
        }

        public boolean isEmpty() {
            return sceneCallbacks.isEmpty();
        }
    }
}
