package com.datamine.analysis.agent.model;

/**
 * 封装 SQL 场景的执行结果。
 */
public record SqlExecutionResult(
        String sql,
        String workflowRunId
) {
}
