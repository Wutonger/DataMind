package com.datamine.analysis.agent.model;

/**
 * 记录单次工具调用的原始返回，用于后续补充结果解析。
 */
public record ToolExecutionRecord(
        String stepId,
        String toolName,
        String rawResult,
        boolean error
) {
}
