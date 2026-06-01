package com.datamine.analysis.agent.tool;

import com.alibaba.cloud.ai.graph.agent.tool.StateAwareToolCallback;
import com.datamine.analysis.skills.tool.ToolExecutionSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class ConnectionAwareToolCallback implements StateAwareToolCallback {

    private final ToolCallback delegate;
    private final Long connectionId;
    private final ObjectMapper objectMapper;
    private final ToolExecutionSupport toolExecutionSupport;
    private final ToolDefinition visibleToolDefinition;
    private final boolean injectConnectionId;

    public ConnectionAwareToolCallback(ToolCallback delegate,
                                       Long connectionId,
                                       ObjectMapper objectMapper,
                                       ToolExecutionSupport toolExecutionSupport) {
        this.delegate = delegate;
        this.connectionId = connectionId;
        this.objectMapper = objectMapper;
        this.toolExecutionSupport = toolExecutionSupport;
        this.injectConnectionId = requiresConnectionId(delegate.getToolDefinition(), objectMapper);
        this.visibleToolDefinition = sanitizeToolDefinition(delegate.getToolDefinition(), objectMapper, this.injectConnectionId);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return visibleToolDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(enrichInput(toolInput));
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String enrichedInput = enrichInput(toolInput);
        // 这里的包装类需要拿到 Agent state，用于在本地记录最近一次 SQL 与查询结果。
        // 但底层 MCP ToolCallback 会把 ToolContext 序列化进 meta 发到 MCP Server，
        // 其中 _AGENT_STATE_ / _AGENT_STATE_FOR_UPDATE_ 不是可安全传输的轻量 JSON，
        // 会导致 HttpClientSseClientTransport 在请求序列化阶段失败。
        // 因此对外部 MCP 工具调用时不透传完整 ToolContext，只在当前包装层本地使用。
        String result = delegate.call(enrichedInput);
        rememberDbExecution(enrichedInput, result, toolContext);
        return result;
    }

    /**
     * 对声明了 connectionId 的工具自动补齐当前连接，
     * 这样 Agent 不需要在每次调用时都显式生成该参数。
     */
    private String enrichInput(String toolInput) {
        if (!injectConnectionId || connectionId == null) {
            return toolInput;
        }

        try {
            Map<String, Object> payload;
            if (!StringUtils.hasText(toolInput) || "null".equals(toolInput.trim())) {
                payload = new LinkedHashMap<>();
            } else {
                payload = objectMapper.readValue(toolInput, new TypeReference<>() {
                });
            }
            payload.put("connectionId", connectionId);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return toolInput;
        }
    }

    private void rememberDbExecution(String enrichedInput, String result, ToolContext toolContext) {
        if (toolContext == null || !isDbExecuteTool(delegate.getToolDefinition().name())) {
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(enrichedInput, new TypeReference<>() {
            });
            Object sqlValue = payload.get("sql");
            if (!(sqlValue instanceof String sql) || !StringUtils.hasText(sql)) {
                return;
            }

            Map<String, Object> parsedResult = parseDbExecuteResult(result);
            if (Boolean.TRUE.equals(parsedResult.get("error"))) {
                return;
            }
            toolExecutionSupport.rememberSqlExecution(toolContext, sql, parsedResult);
        } catch (Exception ex) {
            log.warn("Failed to remember db_execute state. toolName={}, reason={}",
                    delegate.getToolDefinition().name(), ex.getMessage());
        }
    }

    private Map<String, Object> parseDbExecuteResult(String result) throws JsonProcessingException {
        if (!StringUtils.hasText(result)) {
            return Map.of();
        }

        JsonNode root = objectMapper.readTree(result);
        if (root.isObject()) {
            return objectMapper.convertValue(root, new TypeReference<>() {
            });
        }

        if (root.isArray()) {
            Iterator<JsonNode> iterator = root.elements();
            while (iterator.hasNext()) {
                JsonNode item = iterator.next();
                JsonNode textNode = item.get("text");
                if (textNode == null || !textNode.isTextual()) {
                    continue;
                }

                String text = textNode.asText();
                if (!StringUtils.hasText(text)) {
                    continue;
                }

                JsonNode payloadNode = objectMapper.readTree(text);
                if (payloadNode.isObject()) {
                    return objectMapper.convertValue(payloadNode, new TypeReference<>() {
                    });
                }
            }
        }

        return Map.of();
    }

    /**
     * 通过工具输入 schema 判断该工具是否真正接收 connectionId。
     */
    private static boolean requiresConnectionId(ToolDefinition toolDefinition, ObjectMapper objectMapper) {
        try {
            JsonNode schema = objectMapper.readTree(toolDefinition.inputSchema());
            return schema.path("properties").has("connectionId");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 对模型隐藏 connectionId，让它只关注真正需要推理的业务参数；
     * 执行时仍由当前包装类自动补齐连接上下文。
     */
    private static ToolDefinition sanitizeToolDefinition(ToolDefinition toolDefinition,
                                                         ObjectMapper objectMapper,
                                                         boolean injectConnectionId) {
        if (!injectConnectionId) {
            return toolDefinition;
        }

        try {
            JsonNode schema = objectMapper.readTree(toolDefinition.inputSchema()).deepCopy();
            JsonNode propertiesNode = schema.path("properties");
            if (propertiesNode.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) propertiesNode).remove("connectionId");
            }

            JsonNode requiredNode = schema.path("required");
            if (requiredNode.isArray()) {
                com.fasterxml.jackson.databind.node.ArrayNode requiredArray =
                        (com.fasterxml.jackson.databind.node.ArrayNode) requiredNode;
                for (int index = requiredArray.size() - 1; index >= 0; index--) {
                    JsonNode item = requiredArray.get(index);
                    if (item != null && "connectionId".equals(item.asText())) {
                        requiredArray.remove(index);
                    }
                }
            }

            return ToolDefinition.builder()
                    .name(toolDefinition.name())
                    .description(toolDefinition.description())
                    .inputSchema(objectMapper.writeValueAsString(schema))
                    .build();
        } catch (Exception ex) {
            log.warn("Failed to sanitize tool definition. toolName={}, reason={}",
                    toolDefinition.name(), ex.getMessage());
            return toolDefinition;
        }
    }

    private boolean isDbExecuteTool(String toolName) {
        return "db_execute".equals(ToolNameNormalizer.canonicalize(toolName));
    }
}
