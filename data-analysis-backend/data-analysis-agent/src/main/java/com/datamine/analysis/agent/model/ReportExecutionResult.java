package com.datamine.analysis.agent.model;

import java.util.Map;

/**
 * 封装报表中心产物生成后的执行结果，包含保存状态与产物信息。
 */
public record ReportExecutionResult(
        String artifactType,
        String sql,
        Map<String, Object> artifactConfig,
        Map<String, Object> rawResult,
        String workflowRunId,
        Long reportId,
        String reportName,
        boolean savedToReportCenter
) {
}
