package com.datamine.analysis.skills.tool;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ToolExecutionSupport {

    private static final String AGENT_STATE_FOR_UPDATE_KEY = "_AGENT_STATE_FOR_UPDATE_";

    public Map<String, Object> success(String content, Map<String, Object> data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", content != null ? content : "");
        response.put("content", content != null ? content : "");
        response.put("data", data != null ? data : Map.of());
        return response;
    }

    public Map<String, Object> failure(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message != null ? message : "");
        response.put("content", message != null ? message : "");
        response.put("data", Map.of());
        return response;
    }

    public String requirePreparedSql(String explicitSql, ToolContext toolContext) {
        if (StringUtils.hasText(explicitSql)) {
            return normalizeGeneratedSql(explicitSql);
        }

        String currentWorkflowRunId = getCurrentWorkflowRunId(toolContext).orElse("");
        Optional<String> latestSqlWorkflowRunId = getStateValue(toolContext, ToolStateKeys.LATEST_SQL_WORKFLOW_RUN_ID, String.class)
                .filter(StringUtils::hasText)
                .map(String::trim);
        Optional<String> rememberedSql = getStateValue(toolContext, ToolStateKeys.LATEST_SQL, String.class)
                .filter(StringUtils::hasText)
                .map(String::trim);
        if (rememberedSql.isPresent() && latestSqlWorkflowRunId.isPresent()
                && latestSqlWorkflowRunId.get().equals(currentWorkflowRunId)) {
            return normalizeGeneratedSql(rememberedSql.get());
        }

        throw new IllegalArgumentException("Prepared SQL is required for this tool");
    }

    public void rememberKnowledgeContext(ToolContext toolContext,
                                         String query,
                                         String summary,
                                         Object citations) {
        putStateValue(toolContext, ToolStateKeys.KNOWLEDGE_QUERY, StringUtils.hasText(query) ? query.trim() : "");
        putStateValue(toolContext, ToolStateKeys.KNOWLEDGE_SUMMARY, StringUtils.hasText(summary) ? summary.trim() : "");
        putStateValue(toolContext, ToolStateKeys.KNOWLEDGE_CITATIONS, citations != null ? citations : List.of());
    }

    public void rememberSqlExecution(ToolContext toolContext, String sql, Map<String, Object> result) {
        String workflowRunId = getCurrentWorkflowRunId(toolContext).orElse("");
        putStateValue(toolContext, ToolStateKeys.LATEST_SQL, StringUtils.hasText(sql) ? trimTrailingSemicolon(sql) : "");
        putStateValue(toolContext, ToolStateKeys.LATEST_SQL_WORKFLOW_RUN_ID, workflowRunId);
        putStateValue(toolContext, ToolStateKeys.LATEST_QUERY_RESULT, result != null ? result : Map.of());
        putStateValue(toolContext, ToolStateKeys.LATEST_QUERY_RESULT_WORKFLOW_RUN_ID, workflowRunId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> resolveLatestQueryResult(ToolContext toolContext) {
        String currentWorkflowRunId = getCurrentWorkflowRunId(toolContext).orElse("");
        Optional<String> latestResultWorkflowRunId = getStateValue(toolContext, ToolStateKeys.LATEST_QUERY_RESULT_WORKFLOW_RUN_ID, String.class)
                .filter(StringUtils::hasText)
                .map(String::trim);
        if (latestResultWorkflowRunId.isEmpty() || !latestResultWorkflowRunId.get().equals(currentWorkflowRunId)) {
            return Map.of();
        }
        return getStateValue(toolContext, ToolStateKeys.LATEST_QUERY_RESULT, Map.class)
                .map(value -> (Map<String, Object>) value)
                .orElseGet(Map::of);
    }

    public Map<String, Object> requireLatestQueryResult(ToolContext toolContext) {
        Map<String, Object> result = resolveLatestQueryResult(toolContext);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Current workflow has no fresh query result. Please execute db_execute in this round before saving the artifact.");
        }
        return result;
    }

    public String stripMarkdown(String text) {
        if (text == null) {
            return null;
        }
        text = text.trim();
        if (text.startsWith("```sql")) {
            text = text.substring(6);
        } else if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```markdown")) {
            text = text.substring(11);
        } else if (text.startsWith("```md")) {
            text = text.substring(5);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }

    public String normalizeGeneratedSql(String text) {
        String normalized = stripMarkdown(text);
        if (normalized == null) {
            return null;
        }

        List<String> statements = splitSqlStatements(normalized);
        if (statements.isEmpty()) {
            return "";
        }

        if (statements.size() > 1) {
            throw new IllegalStateException(
                    "Generated multiple SQL statements. Current tools only support one SQL statement. Do not return multiple statements separated by semicolons. Please rewrite the logic as one executable SQL query, using a subquery or CTE if needed.");
        }

        return trimTrailingSemicolon(statements.get(0));
    }

    public List<String> splitSqlStatements(String sqlScript) {
        List<String> statements = new ArrayList<>();
        if (sqlScript == null || sqlScript.isBlank()) {
            return statements;
        }

        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sqlScript.length(); i++) {
            char ch = sqlScript.charAt(i);
            char next = i + 1 < sqlScript.length() ? sqlScript.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(ch);
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                current.append(ch);
                if (ch == '*' && next == '/') {
                    current.append(next);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && !inBacktick) {
                if (ch == '-' && next == '-') {
                    current.append(ch).append(next);
                    i++;
                    inLineComment = true;
                    continue;
                }
                if (ch == '/' && next == '*') {
                    current.append(ch).append(next);
                    i++;
                    inBlockComment = true;
                    continue;
                }
            }

            if (ch == '\'' && !inDoubleQuote && !inBacktick) {
                inSingleQuote = !inSingleQuote;
                current.append(ch);
                continue;
            }

            if (ch == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
                current.append(ch);
                continue;
            }

            if (ch == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick;
                current.append(ch);
                continue;
            }

            if (ch == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                String statement = trimTrailingSemicolon(current.toString().trim());
                if (!statement.isBlank()) {
                    statements.add(statement);
                }
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        String tail = trimTrailingSemicolon(current.toString().trim());
        if (!tail.isBlank()) {
            statements.add(tail);
        }

        return statements;
    }

    public String trimTrailingSemicolon(String sql) {
        if (sql == null) {
            return null;
        }

        String normalized = sql.trim();
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private void putStateValue(ToolContext toolContext, String key, Object value) {
        if (toolContext == null || !StringUtils.hasText(key) || value == null) {
            return;
        }

        ToolContextHelper.getStateForUpdate(toolContext)
                .ifPresent(stateForUpdate -> stateForUpdate.put(key, value));
    }

    private <T> Optional<T> getStateValue(ToolContext toolContext, String key, Class<T> valueType) {
        if (toolContext == null || !StringUtils.hasText(key)) {
            return Optional.empty();
        }
        Map<String, Object> context = ToolContextHelper.getAllContext(toolContext);
        // 同一轮工具执行过程中，pending state 应优先覆盖历史 merge state，
        // 否则聊天长会话里会先读到上一轮遗留值，导致本轮刚写入的查询结果失效。
        Optional<T> stateForUpdateValue = readPendingStateValue(
                context.get(AGENT_STATE_FOR_UPDATE_KEY),
                key,
                valueType
        );
        if (stateForUpdateValue.isPresent()) {
            return stateForUpdateValue;
        }

        Optional<T> stateUpdateValue = readPendingStateValue(
                context.get(RunnableConfig.STATE_UPDATE_METADATA_KEY),
                key,
                valueType
        );
        if (stateUpdateValue.isPresent()) {
            return stateUpdateValue;
        }

        return ToolContextHelper.getState(toolContext)
                .flatMap(state -> readStateValue(state, key, valueType));
    }

    private Optional<String> getCurrentWorkflowRunId(ToolContext toolContext) {
        return getStateValue(toolContext, ToolStateKeys.CURRENT_WORKFLOW_RUN_ID, String.class)
                .filter(StringUtils::hasText)
                .map(String::trim);
    }

    private <T> Optional<T> readStateValue(OverAllState state, String key, Class<T> valueType) {
        try {
            return state.value(key, valueType);
        } catch (ClassCastException ex) {
            Object rawValue = state.data().get(key);
            if (valueType.isInstance(rawValue)) {
                return Optional.of(valueType.cast(rawValue));
            }
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> readPendingStateValue(Object rawContainer, String key, Class<T> valueType) {
        if (!(rawContainer instanceof Map<?, ?> container)) {
            return Optional.empty();
        }

        Object rawValue = ((Map<String, Object>) container).get(key);
        if (valueType.isInstance(rawValue)) {
            return Optional.of(valueType.cast(rawValue));
        }
        return Optional.empty();
    }
}
