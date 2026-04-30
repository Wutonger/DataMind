package com.datamine.analysis.mcp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class McpClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serverUrl;
    private McpSyncClient syncClient;
    private boolean initialized = false;

    public McpClient() {
        this.serverUrl = System.getProperty("mcp.server.url", "http://localhost:8081");
        try {
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(serverUrl).build();
            this.syncClient = io.modelcontextprotocol.client.McpClient.sync(transport).build();
            this.syncClient.initialize();
            this.initialized = true;
            log.info("MCP Client connected successfully. target={}", serverUrl);
        } catch (Exception e) {
            Throwable rootCause = rootCause(e);
            log.warn(
                    "MCP Server is unavailable, switching to degraded mode. Web API will keep starting, but MCP tools are disabled. target={}, reason={}",
                    serverUrl,
                    summarizeException(rootCause)
            );
            log.info("To enable MCP tools, start data-analysis-mcp-server and retry. target={}", serverUrl);
            log.debug("MCP initialization stack trace", e);
            this.initialized = false;
        }
    }

    public List<McpSchema.Tool> listTools() {
        if (!initialized) {
            return List.of();
        }
        try {
            McpSchema.ListToolsResult result = syncClient.listTools();
            return result.tools();
        } catch (Exception e) {
            logConnectivityIssue("listTools", e);
            return List.of();
        }
    }

    public Map<String, String> getToolDescriptions() {
        if (!initialized) {
            return Map.of();
        }
        return listTools().stream()
                .collect(java.util.stream.Collectors.toMap(
                        McpSchema.Tool::name,
                        McpSchema.Tool::description
                ));
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Optional<McpSyncClient> getSyncClient() {
        return initialized ? Optional.ofNullable(syncClient) : Optional.empty();
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        if (!initialized) {
            return unavailableResult("MCP 服务当前不可用，请先启动 data-analysis-mcp-server（默认地址: " + serverUrl + "）");
        }
        long startTime = System.currentTimeMillis();
        try {
            McpSchema.CallToolResult result = syncClient.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            long duration = System.currentTimeMillis() - startTime;
            log.info("[MCP] Tool '{}' executed in {}ms, content: {}", toolName, duration, result.content());
            return Map.of("content", result.content(), "isError", result.isError(), "duration", duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Throwable rootCause = rootCause(e);

            if (isConnectivityIssue(rootCause)) {
                log.warn(
                        "[MCP] Tool '{}' is unavailable after {}ms. Cannot reach MCP Server. target={}, reason={}",
                        toolName,
                        duration,
                        serverUrl,
                        summarizeException(rootCause)
                );
                log.debug("[MCP] Tool '{}' connectivity failure stack trace", toolName, e);
                return Map.of(
                        "error", true,
                        "message", "MCP 服务连接失败，请确认 data-analysis-mcp-server 已启动（地址: " + serverUrl + "）",
                        "duration", duration
                );
            }

            log.error(
                    "[MCP] Tool '{}' failed after {}ms. reason={}",
                    toolName,
                    duration,
                    summarizeException(rootCause)
            );
            log.debug("[MCP] Tool '{}' failure stack trace", toolName, e);
            return Map.of(
                    "error", true,
                    "message", "MCP 工具调用失败: " + summarizeException(rootCause),
                    "duration", duration
            );
        }
    }

    private Map<String, Object> unavailableResult(String message) {
        return Map.of("error", true, "message", message);
    }

    private void logConnectivityIssue(String operation, Exception exception) {
        Throwable rootCause = rootCause(exception);
        if (isConnectivityIssue(rootCause)) {
            log.warn(
                    "[MCP] {} skipped because MCP Server is unreachable. target={}, reason={}",
                    operation,
                    serverUrl,
                    summarizeException(rootCause)
            );
            log.debug("[MCP] {} connectivity failure stack trace", operation, exception);
            return;
        }

        log.error("[MCP] {} failed. reason={}", operation, summarizeException(rootCause));
        log.debug("[MCP] {} failure stack trace", operation, exception);
    }

    private boolean isConnectivityIssue(Throwable throwable) {
        return throwable instanceof ConnectException
                || (throwable != null
                && throwable.getMessage() != null
                && throwable.getMessage().toLowerCase().contains("connection refused"));
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current != null ? current : throwable;
    }

    private String summarizeException(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> dbListTables(Long connectionId) {
        try {
            Map<String, Object> result = callTool("dbListTables", Map.of("connectionId", connectionId));
            if (result.containsKey("error") && Boolean.TRUE.equals(result.get("error"))) {
                log.error("dbListTables error: {}", result.get("message"));
                return List.of();
            }
            Object content = result.get("content");
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                if (!contentList.isEmpty()) {
                    Object item = contentList.get(0);
                    if (item instanceof McpSchema.TextContent textContent) {
                        return objectMapper.readValue(textContent.text(), new TypeReference<List<Map<String, Object>>>() {});
                    }
                }
            }
            return List.of();
        } catch (Exception e) {
            log.error("dbListTables failed: {} - {}", e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> dbGetColumns(Long connectionId, String tableName) {
        try {
            Map<String, Object> result = callTool("dbGetColumns", Map.of("connectionId", connectionId, "tableName", tableName));
            if (result.containsKey("error") && Boolean.TRUE.equals(result.get("error"))) {
                log.error("dbGetColumns error: {}", result.get("message"));
                return List.of();
            }
            Object content = result.get("content");
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                if (!contentList.isEmpty()) {
                    Object item = contentList.get(0);
                    if (item instanceof McpSchema.TextContent textContent) {
                        return objectMapper.readValue(textContent.text(), new TypeReference<List<Map<String, Object>>>() {});
                    }
                }
            }
            return List.of();
        } catch (Exception e) {
            log.error("dbGetColumns failed: {} - {}", e.getMessage(), e);
            return List.of();
        }
    }

    public String dbGetSchema(Long connectionId, List<String> tableNames) {
        try {
            Map<String, Object> args = new java.util.HashMap<>();
            args.put("connectionId", connectionId);
            if (tableNames != null && !tableNames.isEmpty()) {
                args.put("tableNames", tableNames);
            }
            Map<String, Object> result = callTool("dbGetSchema", args);
            if (result.containsKey("error") && Boolean.TRUE.equals(result.get("error"))) {
                return "";
            }
            Object content = result.get("content");
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                if (!contentList.isEmpty()) {
                    Object item = contentList.get(0);
                    if (item instanceof McpSchema.TextContent textContent) {
                        return textContent.text();
                    }
                }
            }
            return "";
        } catch (Exception e) {
            log.error("dbGetSchema failed: {} - {}", e.getMessage(), e);
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> dbExecute(Long connectionId, String sql) {
        try {
            Map<String, Object> result = callTool("dbExecute", Map.of("connectionId", connectionId, "sql", sql));
            if (result.containsKey("error") && Boolean.TRUE.equals(result.get("error"))) {
                return result;
            }
            Object content = result.get("content");
            if (content instanceof List) {
                List<?> contentList = (List<?>) content;
                if (!contentList.isEmpty()) {
                    Object item = contentList.get(0);
                    if (item instanceof McpSchema.TextContent textContent) {
                        String text = textContent.text();
                        return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
                    }
                }
            }
            return Map.of("error", true, "message", "Invalid response format");
        } catch (Exception e) {
            log.error("dbExecute failed: {} - {}", e.getMessage(), e);
            return Map.of("error", true, "message", e.getMessage());
        }
    }
}
